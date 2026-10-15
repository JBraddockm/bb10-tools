package com.jbraddockm.core.daemon;

import static com.jbraddockm.core.daemon.Checks.check;
import static com.jbraddockm.core.daemon.Checks.equal;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.command.ProgressPhase;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Drives the real server with a stub service through pipes and checks the protocol rules: hello,
 * decoding errors, session lifecycle, ordering, exactly one final event, and the drain on end of
 * input.
 */
public final class DaemonConformanceCheck {

  private DaemonConformanceCheck() {}

  /** Phases of the stub. */
  private enum StubPhase implements ProgressPhase {
    WORK;

    @Override
    public String wireName() {
      return "work";
    }
  }

  /** A session that records whether it was closed. */
  private static final class StubSession implements ServiceSession {
    private final String host;
    private volatile boolean closed;

    StubSession(String host) {
      this.host = host;
    }

    @Override
    public void close() {
      closed = true;
    }
  }

  /** The service under test. */
  private static final class StubService implements Service<StubSession> {
    final CountDownLatch establishGate = new CountDownLatch(1);
    final CountDownLatch blockGate = new CountDownLatch(1);
    final CountDownLatch holdGate = new CountDownLatch(1);
    final List<StubSession> opened = new ArrayList<>();

    @Override
    public ServiceInfo info() {
      return new ServiceInfo("stub", "9.9");
    }

    @Override
    public List<CommandSpec<StubSession>> commands() {
      return List.of(
          new CommandSpec.SessionScoped<StubSession>(
              "echo",
              params -> {
                var text = params.requireString("text");
                return (session, reply) -> {
                  reply.onLog(LogLevel.INFO, "echoing");
                  reply.onProgress(Progress.ofPercent(50, StubPhase.WORK));
                  reply.result("echo", Json.object("text", text, "host", session.host), null);
                  reply.succeed();
                };
              }),
          new CommandSpec.SessionScoped<StubSession>(
              "boom",
              params ->
                  (session, reply) -> {
                    throw new IllegalStateException("kaboom");
                  }),
          new CommandSpec.SessionScoped<StubSession>(
              "silent", params -> (session, reply) -> reply.onLog(LogLevel.DEBUG, "nothing more")),
          new CommandSpec.SessionScoped<StubSession>(
              "block",
              params ->
                  (session, reply) -> {
                    try {
                      blockGate.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                      Thread.currentThread().interrupt();
                    }
                    reply.succeed();
                  }),
          new CommandSpec.SessionScoped<StubSession>(
              "hold",
              params ->
                  (session, reply) -> {
                    try {
                      holdGate.await(10, TimeUnit.SECONDS);
                    } catch (InterruptedException _) {
                      Thread.currentThread().interrupt();
                    }
                    reply.succeed();
                  }),
          new CommandSpec.Standalone<StubSession>(
              "ping",
              params ->
                  reply -> {
                    reply.event("pong", Map.of("n", 1L));
                    reply.result("ping", Json.object("pong", true), null);
                    reply.succeed();
                  }));
    }

    @Override
    public StubSession open(RequestParams params) {
      var session = new StubSession(params.requireString("host"));
      synchronized (opened) {
        opened.add(session);
      }
      return session;
    }

    @Override
    public Establishment establish(StubSession session, Reply reply) {
      if (session.host.equals("bad")) {
        return new Establishment.Rejected(
            new ServiceError(CoreErrorCode.CONNECTION_FAILED, "no route"));
      }
      if (session.host.equals("slow")) {
        try {
          establishGate.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
        }
      }
      return new Establishment.Established(Json.object("host", session.host));
    }
  }

  /** Collects the server's output lines as parsed JSON objects. */
  private static final class EventSink extends ByteArrayOutputStream {
    final List<Map<?, ?>> events = new ArrayList<>();

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      super.write(bytes, offset, length);
      var text = toString(StandardCharsets.UTF_8);
      int newline;
      while ((newline = text.indexOf('\n')) >= 0) {
        events.add((Map<?, ?>) Json.parse(text.substring(0, newline)));
        text = text.substring(newline + 1);
      }
      reset();
      super.write(
          text.getBytes(StandardCharsets.UTF_8), 0, text.getBytes(StandardCharsets.UTF_8).length);
      notifyAll();
    }

    synchronized List<Map<?, ?>> snapshot() {
      return List.copyOf(events);
    }

    synchronized boolean await(Predicate<List<Map<?, ?>>> condition) throws InterruptedException {
      var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (!condition.test(events)) {
        var remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(this, remaining);
      }
      return true;
    }
  }

  private static boolean isDone(Map<?, ?> event, Object id) {
    return "done".equals(event.get("type")) && java.util.Objects.equals(id, event.get("id"));
  }

  private static Map<?, ?> doneOf(EventSink sink, Object id) throws InterruptedException {
    check(
        sink.await(events -> events.stream().anyMatch(event -> isDone(event, id))),
        "done for " + id);
    return sink.snapshot().stream().filter(event -> isDone(event, id)).findFirst().orElse(Map.of());
  }

  private static List<String> typesOf(EventSink sink, Object id) {
    return sink.snapshot().stream()
        .filter(event -> java.util.Objects.equals(id, event.get("id")))
        .map(event -> (String) event.get("type"))
        .toList();
  }

  private static Object errorCode(Map<?, ?> done) {
    return done.get("error") instanceof Map<?, ?> error ? error.get("code") : null;
  }

  /** The typed readers of request parameters. */
  private static void parameters() {
    var params =
        new RequestParams(Map.of("count", 5L, "text", "five", "big", 99L, "flag", Boolean.TRUE));
    equal(5, params.optionalInteger("count", 1, 10).getAsInt(), "an optional integer");
    check(params.optionalInteger("missing", 1, 10).isEmpty(), "an absent optional integer");
    for (var name : List.of("big", "text")) {
      var rejected = false;
      try {
        params.optionalInteger(name, 1, 10);
      } catch (com.jbraddockm.core.error.InvalidArgumentException failure) {
        rejected = failure.parameter().equals(name);
      }
      check(rejected, "an optional integer that is out of range or not a number: " + name);
    }
    var nested = new RequestParams(Map.of("inner", Map.of("a", "x", "b", 1L), "text", "no"));
    equal(
        java.util.Set.of("a", "b"),
        nested.requireObject("inner").names(),
        "a nested object and the names in it");
    equal(
        "x", nested.requireObject("inner").requireString("a"), "its members are read the same way");
    check(nested.optionalObject("missing").isEmpty(), "an absent object");
    for (var name : List.of("text", "missing")) {
      var rejected = false;
      try {
        nested.requireObject(name);
      } catch (com.jbraddockm.core.error.InvalidArgumentException failure) {
        rejected = failure.parameter().equals(name);
      }
      check(rejected, "a required object that is absent or not an object: " + name);
    }
    check(params.flag("flag"), "a flag that is set");
    check(!params.flag("missing"), "a flag that is absent");
  }

  /**
   * Runs all checks.
   *
   * @param arguments ignored
   * @throws Exception if the harness itself fails
   */
  public static void main(String[] arguments) throws Exception {
    parameters();
    var service = new StubService();
    var sink = new EventSink();
    var toServer = new PipedOutputStream();
    var fromClient = new PipedInputStream(toServer, 1 << 16);
    var server =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    DaemonServer.serve(service, fromClient, sink, Duration.ofSeconds(5));
                  } catch (IOException failure) {
                    failure.printStackTrace();
                  }
                });
    java.util.function.Consumer<String> send =
        line -> {
          try {
            toServer.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            toServer.flush();
          } catch (IOException failure) {
            throw new IllegalStateException(failure);
          }
        };

    // hello
    check(sink.await(events -> !events.isEmpty()), "hello arrives");
    var hello = sink.snapshot().getFirst();
    equal("hello", hello.get("type"), "hello type");
    equal(1L, hello.get("protocol"), "protocol version");
    equal("stub", hello.get("tool"), "tool name");
    equal(
        List.of(
            "connect", "disconnect", "shutdown", "echo", "boom", "silent", "block", "hold", "ping"),
        hello.get("methods"),
        "advertised methods");

    // decoding errors
    send.accept("not json");
    send.accept("{\"method\":\"ping\"}");
    send.accept("{\"id\":1}");
    send.accept("{\"id\":2,\"method\":\"nope\"}");
    send.accept("{\"id\":3,\"method\":\"echo\",\"params\":{\"session\":\"s9\",\"text\":\"x\"}}");
    send.accept("{\"id\":4,\"method\":\"echo\",\"params\":{\"session\":\"s9\"}}");
    check(
        sink.await(
            events ->
                events.stream().filter(event -> "done".equals(event.get("type"))).count() >= 6),
        "six rejections");
    var dones = sink.snapshot().stream().filter(event -> "done".equals(event.get("type"))).toList();
    equal(CoreErrorCode.INVALID_REQUEST.wireName(), errorCode(dones.get(0)), "not json");
    check(
        dones.get(0).containsKey("id") && dones.get(0).get("id") == null,
        "null id for undecodable line");
    equal(CoreErrorCode.INVALID_REQUEST.wireName(), errorCode(dones.get(1)), "missing id");
    equal(CoreErrorCode.INVALID_REQUEST.wireName(), errorCode(dones.get(2)), "missing method");
    equal(CoreErrorCode.UNKNOWN_METHOD.wireName(), errorCode(doneOf(sink, 2L)), "unknown method");
    equal(CoreErrorCode.UNKNOWN_SESSION.wireName(), errorCode(doneOf(sink, 3L)), "unknown session");
    equal(
        CoreErrorCode.INVALID_ARGUMENT.wireName(),
        errorCode(doneOf(sink, 4L)),
        "invalid argument beats unknown session");

    // standalone command with a custom event
    send.accept("{\"id\":\"p\",\"method\":\"ping\"}");
    doneOf(sink, "p");
    equal(
        List.of("pong", "result", "done"),
        typesOf(sink, "p"),
        "ping events in order, string id echoed");

    // connect: failure leaves no session
    send.accept("{\"id\":10,\"method\":\"connect\",\"params\":{\"host\":\"bad\"}}");
    equal(
        CoreErrorCode.CONNECTION_FAILED.wireName(),
        errorCode(doneOf(sink, 10L)),
        "rejected connect");
    send.accept("{\"id\":11,\"method\":\"echo\",\"params\":{\"session\":\"s1\",\"text\":\"x\"}}");
    equal(
        CoreErrorCode.UNKNOWN_SESSION.wireName(),
        errorCode(doneOf(sink, 11L)),
        "no session after failed connect");

    // connect: still connecting
    send.accept("{\"id\":12,\"method\":\"connect\",\"params\":{\"host\":\"slow\"}}");
    send.accept("{\"id\":13,\"method\":\"echo\",\"params\":{\"session\":\"s2\",\"text\":\"x\"}}");
    var early = doneOf(sink, 13L);
    equal(
        CoreErrorCode.UNKNOWN_SESSION.wireName(),
        errorCode(early),
        "not usable before the connect reply");
    check(
        String.valueOf(((Map<?, ?>) early.get("error")).get("message"))
            .contains("still connecting"),
        "message says still connecting");
    service.establishGate.countDown();
    var connected = doneOf(sink, 12L);
    equal(Boolean.TRUE, connected.get("ok"), "slow connect succeeds");
    var connectResult =
        sink.snapshot().stream()
            .filter(
                event ->
                    Long.valueOf(12).equals(event.get("id")) && "result".equals(event.get("type")))
            .findFirst()
            .orElseThrow();
    equal(
        Json.object("session", "s2", "host", "slow"),
        connectResult.get("data"),
        "connect result: session first");

    // a normal command on the session
    send.accept("{\"id\":14,\"method\":\"echo\",\"params\":{\"session\":\"s2\",\"text\":\"hi\"}}");
    doneOf(sink, 14L);
    equal(
        List.of("log", "progress", "result", "done"),
        typesOf(sink, 14L),
        "event order of a command");

    // failures inside commands still end the request exactly once
    send.accept("{\"id\":15,\"method\":\"boom\",\"params\":{\"session\":\"s2\"}}");
    equal(
        CoreErrorCode.INTERNAL.wireName(),
        errorCode(doneOf(sink, 15L)),
        "exception becomes INTERNAL");
    send.accept("{\"id\":16,\"method\":\"silent\",\"params\":{\"session\":\"s2\"}}");
    equal(
        CoreErrorCode.INTERNAL.wireName(),
        errorCode(doneOf(sink, 16L)),
        "command that forgets to finish");
    equal(1L, typesOf(sink, 16L).stream().filter("done"::equals).count(), "exactly one done");

    // ordering within a session
    send.accept("{\"id\":20,\"method\":\"block\",\"params\":{\"session\":\"s2\"}}");
    send.accept(
        "{\"id\":21,\"method\":\"echo\",\"params\":{\"session\":\"s2\",\"text\":\"later\"}}");
    Thread.sleep(300);
    check(typesOf(sink, 21L).isEmpty(), "second command waits for the first on one session");
    service.blockGate.countDown();
    doneOf(sink, 21L);
    var order =
        sink.snapshot().stream()
            .filter(event -> "done".equals(event.get("type")))
            .map(event -> event.get("id"))
            .toList();
    check(order.indexOf(20L) < order.indexOf(21L), "first command finished before the second");

    // disconnect closes the session and is idempotent
    send.accept("{\"id\":30,\"method\":\"disconnect\",\"params\":{\"session\":\"s2\"}}");
    doneOf(sink, 30L);
    check(
        service.opened.stream()
            .filter(session -> session.host.equals("slow"))
            .allMatch(session -> session.closed),
        "session closed on disconnect");
    send.accept("{\"id\":31,\"method\":\"disconnect\",\"params\":{\"session\":\"s2\"}}");
    equal(Boolean.TRUE, doneOf(sink, 31L).get("ok"), "second disconnect is fine");

    // drain: accepted work is answered after the input ended
    send.accept("{\"id\":40,\"method\":\"connect\",\"params\":{\"host\":\"ok\"}}");
    doneOf(sink, 40L);
    send.accept("{\"id\":41,\"method\":\"hold\",\"params\":{\"session\":\"s3\"}}");
    toServer.close();
    Thread.sleep(200);
    check(server.isAlive(), "server waits for accepted requests after end of input");
    service.holdGate.countDown();
    server.join(8000);
    check(!server.isAlive(), "server exits once everything was answered");
    equal(Boolean.TRUE, doneOf(sink, 41L).get("ok"), "accepted request answered during the drain");

    System.exit(Checks.finish("bb-core conformance"));
  }
}
