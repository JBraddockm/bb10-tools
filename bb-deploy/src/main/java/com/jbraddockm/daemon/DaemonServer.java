package com.jbraddockm.daemon;

import com.jbraddockm.DeployError;
import com.jbraddockm.ErrorCode;
import com.jbraddockm.device.CommandOutcome;
import com.jbraddockm.device.DeviceCommand;
import com.jbraddockm.device.DeviceSession;
import com.jbraddockm.device.DeviceTarget;
import com.jbraddockm.device.InvalidArgumentException;
import com.jbraddockm.device.StandardOutputCapture;
import com.jbraddockm.internal.Json;
import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.DeviceInfo;
import com.jbraddockm.output.DeviceSummary;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Phaser;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

/**
 * Serves the JSON protocol on standard input and output until the input ends or a client sends
 * {@code shutdown}.
 *
 * <p>Requests arrive one per line. Each is answered with log and progress events, at most one
 * result, and one final event, all carrying the request's id. Requests for different sessions run
 * concurrently; requests for one session run in the order they arrived.
 *
 * <p>When the input ends or {@code shutdown} arrives, requests that were already accepted are still
 * answered: the daemon waits for them, up to {@link #DRAIN_TIMEOUT}, before it exits. So a client
 * may send its requests and close the input straight away.
 */
public final class DaemonServer {

  /**
   * How long accepted requests may still run after the input ends. Long enough for a device scan or
   * a large install; a stuck command still cannot keep the process alive forever.
   */
  static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(5);

  private static final int PROTOCOL_VERSION = 1;
  private static final String TOOL_NAME = "blackberry-deploy";
  private static final String TOOL_VERSION = "1.5.0";
  private final EventWriter events;
  private final Map<String, SessionWorker> sessions = new ConcurrentHashMap<>();

  /**
   * Sessions whose {@code connect} has not finished: not usable yet, but they must be abandoned on
   * exit.
   */
  private final Map<String, SessionWorker> connecting = new ConcurrentHashMap<>();

  private final ExecutorService deviceless =
      Executors.newCachedThreadPool(daemonThreads("deviceless"));
  private final AtomicInteger sessionCounter = new AtomicInteger();

  /** One party for the reading loop, plus one for each accepted task that has not finished. */
  private final Phaser inFlight = new Phaser(1);

  private volatile boolean stopRequested;

  private DaemonServer(EventWriter events) {
    this.events = events;
  }

  /**
   * Runs the daemon on the process's standard input and output.
   *
   * <p>The protocol owns the real standard output. Anything else that the process prints, such as
   * the Deploy tool's debug output, is redirected to standard error so it cannot corrupt the
   * protocol. Output that belongs to a running command is captured for that command instead.
   *
   * @throws IOException if reading the input fails
   */
  public static void run() throws IOException {
    var protocolStream =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    // System.out still goes to standard error, except while a command captures it (see
    // StandardOutputCapture). This must happen before the Deploy tool is loaded.
    StandardOutputCapture.install(System.err);
    new DaemonServer(new EventWriter(protocolStream)).serve();
  }

  /**
   * Threads that never keep the process alive: a stuck command must not prevent the daemon from
   * exiting.
   */
  static ThreadFactory daemonThreads(String namePrefix) {
    return Thread.ofPlatform().daemon().name(namePrefix + "-", 0).factory();
  }

  private static DeployError toError(RuntimeException failure) {
    return switch (failure) {
      case RequestRejectedException rejected -> rejected.error();
      case InvalidArgumentException invalid -> invalid.toDeployError();
      case IllegalArgumentException illegal ->
          new DeployError(ErrorCode.INVALID_ARGUMENT, String.valueOf(illegal.getMessage()));
      default -> DeployError.internal(failure);
    };
  }

  private static <T extends CommandResult> void runOnSession(
      SessionWorker worker, String methodName, DeviceCommand<T> command, Reply reply) {
    reply.complete(methodName, worker.execute(command, reply));
  }

  private static <T extends CommandResult> void runWithoutDevice(
      String methodName, DeviceCommand<T> command, Reply reply) {
    try (var session = DeviceSession.withoutDevice()) {
      reply.complete(methodName, session.execute(command, reply));
    }
  }

  /**
   * Guarantees that the client always gets its final event, even if something unexpected goes
   * wrong. This is the one place where {@link Throwable} is caught on purpose.
   */
  private static void guarded(Reply reply, Runnable task) {
    try {
      task.run();
    } catch (Throwable failure) {
      reply.fail(DeployError.internal(failure));
    }
  }

  private void serve() throws IOException {
    events.write(
        Json.object(
            "type", "hello",
            "protocol", PROTOCOL_VERSION,
            "tool", TOOL_NAME,
            "version", TOOL_VERSION,
            "methods", Method.wireNames()));

    try (var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line;
      while (!stopRequested && (line = input.readLine()) != null) {
        if (!line.isBlank()) {
          handle(line);
        }
      }
    } finally {
      awaitInFlight();
      abandonAllSessions();
    }
  }

  /** Lets accepted requests send their final event, unless they take longer than the timeout. */
  private void awaitInFlight() {
    var phase = inFlight.arriveAndDeregister();
    try {
      inFlight.awaitAdvanceInterruptibly(phase, DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException _) {
      System.err.println(
          "bb-deploy: requests still running after "
              + DRAIN_TIMEOUT.toSeconds()
              + " s, exiting without their reply");
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Runs a task on {@code executor} and counts it as in flight until it ends, so that the daemon
   * does not exit underneath it.
   */
  private void runTracked(Executor executor, Runnable task) {
    inFlight.register();
    try {
      executor.execute(
          () -> {
            try {
              task.run();
            } finally {
              inFlight.arriveAndDeregister();
            }
          });
    } catch (RuntimeException rejected) {
      inFlight.arriveAndDeregister();
      throw rejected;
    }
  }

  /** {@link SessionWorker#closeAfterPending}, counted as in flight until {@code afterwards} ran. */
  private void closeTracked(SessionWorker worker, Runnable afterwards) {
    inFlight.register();
    try {
      worker.closeAfterPending(
          () -> {
            try {
              afterwards.run();
            } finally {
              inFlight.arriveAndDeregister();
            }
          });
    } catch (RuntimeException rejected) {
      inFlight.arriveAndDeregister();
      throw rejected;
    }
  }

  private void abandonAllSessions() {
    Stream.concat(sessions.values().stream(), connecting.values().stream())
        .forEach(SessionWorker::abandon);
    sessions.clear();
    connecting.clear();
  }

  private void handle(String line) {
    switch (RequestDecoder.decode(line)) {
      case RequestDecoder.Decoding.Rejected(var id, var error) -> Reply.to(events, id).fail(error);
      case RequestDecoder.Decoding.Accepted(var request) -> process(request);
    }
  }

  private void process(Request request) {
    var reply = Reply.to(events, request.id());
    try {
      var method =
          Method.byWireName(request.method())
              .orElseThrow(
                  () ->
                      new RequestRejectedException(
                          new DeployError(
                              ErrorCode.UNKNOWN_METHOD,
                              "unknown method '" + request.method() + "'")));
      dispatch(method, request.params(), reply);
    } catch (RuntimeException failure) {
      reply.fail(toError(failure));
    }
  }

  private void dispatch(Method method, RequestParams params, Reply reply) {
    switch (method) {
      case CONNECT -> connect(params, reply);
      case DISCONNECT -> disconnect(params, reply);
      case SHUTDOWN -> {
        reply.succeed();
        stopRequested = true;
      }
      default -> runDeviceCommand(method, params, reply);
    }
  }

  /**
   * {@code connect} is the first command of a session: it logs in and reads the device information,
   * then answers with the session id and a device summary. If that fails, the answer is the same
   * error any command would give and no session exists. The session id is usable only after the
   * reply.
   */
  private void connect(RequestParams params, Reply reply) {
    var target =
        new DeviceTarget(
            params.requireString("host"),
            params.integer("port", DeviceTarget.DEFAULT_PORT, 1, 65_535),
            params.optionalString("password").orElse(""));
    var worker = new SessionWorker("s" + sessionCounter.incrementAndGet(), target);
    connecting.put(worker.id(), worker);
    runTracked(worker::submit, () -> guarded(reply, () -> finishConnect(worker, reply)));
  }

  private void finishConnect(SessionWorker worker, Reply reply) {
    switch (worker.execute(new DeviceCommand.ListDeviceInfo(), reply)) {
      case CommandOutcome.Succeeded<DeviceInfo>(var info, _) when info.isPresent() -> {
        connecting.remove(worker.id());
        sessions.put(worker.id(), worker);
        reply.result(
            "connect",
            Json.object(
                "session", worker.id(),
                "host", worker.target().host(),
                "port", worker.target().port(),
                "device", DeviceSummary.of(info.get()).toJson()),
            null);
        reply.succeed();
      }
      case CommandOutcome.Succeeded<DeviceInfo> _ ->
          rejectConnection(
              worker,
              reply,
              new DeployError(ErrorCode.INTERNAL, "the device returned no information"));
      case CommandOutcome.Failed<DeviceInfo>(var error, _, _) ->
          rejectConnection(worker, reply, error);
    }
  }

  /** A failed connect leaves no session behind: the login and the stored password are discarded. */
  private void rejectConnection(SessionWorker worker, Reply reply, DeployError error) {
    connecting.remove(worker.id());
    closeTracked(worker, () -> reply.fail(error));
  }

  private void disconnect(RequestParams params, Reply reply) {
    var worker = sessions.remove(params.requireString("session"));
    if (worker == null) {
      reply.succeed(); // disconnecting is idempotent
    } else {
      closeTracked(worker, reply::succeed);
    }
  }

  private SessionWorker workerFor(String sessionId) {
    var worker = sessions.get(sessionId);
    if (worker != null) {
      return worker;
    }
    var message =
        connecting.containsKey(sessionId)
            ? "session '"
                + sessionId
                + "' is still connecting: wait for the connect reply before using it"
            : "no such session: " + sessionId;
    throw new RequestRejectedException(new DeployError(ErrorCode.UNKNOWN_SESSION, message));
  }

  private void runDeviceCommand(Method method, RequestParams params, Reply reply) {
    var command = method.command(params);
    if (command.requiresDevice()) {
      var worker = workerFor(params.requireString("session"));
      runTracked(
          worker::submit,
          () -> guarded(reply, () -> runOnSession(worker, method.wireName(), command, reply)));
    } else {
      runTracked(
          deviceless,
          () -> guarded(reply, () -> runWithoutDevice(method.wireName(), command, reply)));
    }
  }
}
