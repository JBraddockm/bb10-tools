package com.jbraddockm.core.daemon;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.TestPhase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A {@link Service} for server tests whose commands answer at once, fail, forget to finish, or wait
 * at a gate that the test opens. The host of a connect selects what its session does: it is
 * established or rejected, and it closes, fails to close, or waits when closing.
 */
final class StubService implements Service<StubService.StubSession> {

  private static final long PATIENCE_SECONDS = 10;
  final CountDownLatch establishGate = new CountDownLatch(1);
  final CountDownLatch blockGate = new CountDownLatch(1);
  final CountDownLatch blockStarted = new CountDownLatch(1);
  final CountDownLatch holdGate = new CountDownLatch(1);
  final CountDownLatch holdStarted = new CountDownLatch(1);
  final CountDownLatch closeGate = new CountDownLatch(1);
  final CountDownLatch closeEnded = new CountDownLatch(1);
  final List<StubSession> opened = Collections.synchronizedList(new ArrayList<>());
  final List<String> order = Collections.synchronizedList(new ArrayList<>());
  private final List<CommandSpec<StubSession>> extraCommands;

  StubService() {
    this(List.of());
  }

  StubService(List<CommandSpec<StubSession>> extraCommands) {
    this.extraCommands = List.copyOf(extraCommands);
  }

  /**
   * Waits at a gate. Returns {@code true} if the gate was opened and {@code false} if the wait was
   * interrupted; a gate that is not opened in time fails the test.
   */
  private static boolean waitAt(CountDownLatch gate) {
    try {
      if (!gate.await(PATIENCE_SECONDS, TimeUnit.SECONDS)) {
        throw new IllegalStateException("a gate was not opened in time");
      }
      return true;
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("stub", "9.9");
  }

  @Override
  public List<CommandSpec<StubSession>> commands() {
    var commands = new ArrayList<CommandSpec<StubSession>>();
    commands.add(
        new CommandSpec.SessionScoped<>(
            "echo",
            params -> {
              var text = params.requireString("text");
              return (session, reply) -> {
                order.add("echo " + text);
                reply.onLog(LogLevel.INFO, "echoing");
                reply.onProgress(Progress.ofPercent(50, TestPhase.UPLOADING));
                reply.result("echo", Json.object("text", text, "host", session.host), null);
                reply.succeed();
              };
            }));
    commands.add(
        new CommandSpec.SessionScoped<>(
            "boom",
            _ ->
                (_, _) -> {
                  throw new IllegalStateException("kaboom");
                }));
    commands.add(
        new CommandSpec.SessionScoped<>(
            "silent", _ -> (_, reply) -> reply.onLog(LogLevel.DEBUG, "nothing more")));
    commands.add(
        new CommandSpec.SessionScoped<>(
            "block",
            _ ->
                (_, reply) -> {
                  order.add("block starts");
                  blockStarted.countDown();
                  waitAt(blockGate);
                  order.add("block ends");
                  reply.succeed();
                }));
    commands.add(
        new CommandSpec.SessionScoped<>(
            "hold",
            _ ->
                (_, reply) -> {
                  holdStarted.countDown();
                  order.add(waitAt(holdGate) ? "hold released" : "hold interrupted");
                  reply.succeed();
                }));
    commands.add(
        new CommandSpec.Standalone<>(
            "ping",
            _ ->
                reply -> {
                  reply.event("pong", Map.of("n", 1L));
                  reply.result("ping", Json.object("pong", true), null);
                  reply.succeed();
                }));
    commands.add(
        new CommandSpec.SessionScoped<>(
            "illegal",
            _ -> {
              throw new IllegalArgumentException("nope");
            }));
    commands.addAll(extraCommands);
    return commands;
  }

  @Override
  public StubSession open(RequestParams params) {
    var host = params.requireString("host");
    var session = new StubSession(host, () -> whenClosing(host));
    opened.add(session);
    return session;
  }

  private void whenClosing(String host) {
    switch (host) {
      case "unclosable" -> throw new IllegalStateException("close failed");
      case "stuck-on-close" -> {
        order.add(waitAt(closeGate) ? "close released" : "close interrupted");
        closeEnded.countDown();
      }
      default -> {
        // an ordinary session closes at once
      }
    }
  }

  @Override
  public Establishment establish(StubSession session, RequestParams params, Reply reply) {
    switch (session.host) {
      case "bad" -> {
        return new Establishment.Rejected(
            new ServiceError(CoreErrorCode.CONNECTION_FAILED, "no route"));
      }
      case "explode" -> throw new IllegalStateException("explosion");
      case "slow" -> waitAt(establishGate);
      default -> {
        // an ordinary host is established at once
      }
    }
    return new Establishment.Established(Json.object("host", session.host));
  }

  /** A session that records whether it was closed, after doing what its host asks on closing. */
  static final class StubSession implements ServiceSession {
    final String host;
    private final Runnable onClose;
    volatile boolean closed;

    StubSession(String host, Runnable onClose) {
      this.host = host;
      this.onClose = onClose;
    }

    @Override
    public void close() {
      onClose.run();
      closed = true;
    }
  }
}
