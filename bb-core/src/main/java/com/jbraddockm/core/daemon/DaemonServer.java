package com.jbraddockm.core.daemon;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
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
 * Serves the JSON protocol until the input ends or a client sends {@code shutdown}.
 *
 * <p>Requests arrive one per line. Each is answered with log and progress events, at most one
 * result, and one final event, all carrying the request's id. Requests for different sessions run
 * concurrently; requests for one session run in the order they arrived.
 *
 * <p>When the input ends or {@code shutdown} arrives, requests that were already accepted are still
 * answered: the daemon waits for them, up to the drain timeout, before it returns. So a client may
 * send its requests and close the input straight away.
 *
 * <p>Sessions stay open between requests until the client sends {@code disconnect}. When the daemon
 * ends, the sessions that are still open are closed. If a request outlives the drain timeout, it is
 * interrupted and its session is left to the process exit.
 *
 * @param <S> the session type of the service
 */
public final class DaemonServer<S extends ServiceSession> {

  /** The version of the line protocol announced in {@code hello}. */
  public static final int PROTOCOL_VERSION = 1;

  /**
   * How long accepted requests may still run after the input ends. Long enough for a device scan or
   * a large transfer; a stuck command still cannot keep the process alive forever.
   */
  static final Duration DRAIN_TIMEOUT = Duration.ofMinutes(5);

  private static final String CONNECT = "connect";
  private static final String DISCONNECT = "disconnect";
  private static final String SHUTDOWN = "shutdown";

  private final Service<S> service;
  private final EventWriter events;
  private final Duration drainTimeout;
  private final Map<String, CommandSpec<S>> commands = new LinkedHashMap<>();
  private final Map<String, SessionWorker<S>> sessions = new ConcurrentHashMap<>();

  /**
   * Sessions whose {@code connect} has not finished: not usable yet, but they must be abandoned on
   * exit.
   */
  private final Map<String, SessionWorker<S>> connecting = new ConcurrentHashMap<>();

  private final ExecutorService standalone =
      Executors.newCachedThreadPool(daemonThreads("standalone"));
  private final AtomicInteger sessionCounter = new AtomicInteger();

  /** One party for the reading loop, plus one for each accepted task that has not finished. */
  private final Phaser inFlight = new Phaser(1);

  private volatile boolean stopRequested;

  private DaemonServer(Service<S> service, EventWriter events, Duration drainTimeout) {
    this.service = service;
    this.events = events;
    this.drainTimeout = drainTimeout;
    for (var spec : service.commands()) {
      var name = spec.wireName();
      if (List.of(CONNECT, DISCONNECT, SHUTDOWN).contains(name)
          || commands.putIfAbsent(name, spec) != null) {
        throw new IllegalArgumentException("method declared twice or reserved: " + name);
      }
    }
  }

  /**
   * Runs the daemon on the process's standard input and output.
   *
   * <p>The protocol owns the real standard output. Anything else the process prints through {@link
   * System#out} is redirected to standard error so it cannot corrupt the protocol.
   *
   * @param service the tool's methods
   * @param <S> the session type
   * @throws IOException if reading the input fails
   */
  public static <S extends ServiceSession> void run(Service<S> service) throws IOException {
    var protocolStream = StandardOutputGuard.claimProtocolStream();
    new DaemonServer<>(service, new EventWriter(protocolStream), DRAIN_TIMEOUT).serve(System.in);
  }

  /**
   * Runs the daemon on explicit streams, which lets tests drive it in-process.
   *
   * @param service the tool's methods
   * @param input where requests are read from; read until it ends
   * @param protocolOutput where events are written
   * @param drainTimeout how long accepted requests may keep running after the input ended
   * @param <S> the session type
   * @throws IOException if reading the input fails
   */
  public static <S extends ServiceSession> void serve(
      Service<S> service, InputStream input, OutputStream protocolOutput, Duration drainTimeout)
      throws IOException {
    var stream = new PrintStream(protocolOutput, false, StandardCharsets.UTF_8);
    new DaemonServer<>(service, new EventWriter(stream), drainTimeout).serve(input);
  }

  /**
   * Threads that never keep the process alive: a stuck command must not prevent the daemon from
   * exiting.
   */
  static ThreadFactory daemonThreads(String namePrefix) {
    return Thread.ofPlatform().daemon().name(namePrefix + "-", 0).factory();
  }

  private static ServiceError toError(RuntimeException failure) {
    return switch (failure) {
      case RequestRejectedException rejected -> rejected.error();
      case InvalidArgumentException invalid -> invalid.toServiceError();
      case IllegalArgumentException illegal ->
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, String.valueOf(illegal.getMessage()));
      default -> ServiceError.internal(failure);
    };
  }

  /**
   * Guarantees that the client always gets its final event, even if something unexpected goes wrong
   * or a command forgets to end the request. This is the one place where {@link Throwable} is
   * caught on purpose.
   */
  private static void guarded(Reply reply, Runnable task) {
    guarded(reply, task, true);
  }

  /**
   * Like {@link #guarded(Reply, Runnable)}, but a task that answers later, from another queue
   * entry, may be exempted from the check that the request was finished when the task returns.
   */
  private static void guarded(Reply reply, Runnable task, boolean mustFinish) {
    try {
      task.run();
      if (mustFinish && !reply.isFinished()) {
        reply.fail(
            new ServiceError(CoreErrorCode.INTERNAL, "the command ended without a final event"));
      }
    } catch (Throwable failure) {
      reply.fail(ServiceError.internal(failure));
    }
  }

  private void serve(InputStream input) throws IOException {
    var methods =
        Stream.concat(Stream.of(CONNECT, DISCONNECT, SHUTDOWN), commands.keySet().stream());
    events.write(
        Json.object(
            "type", "hello",
            "protocol", PROTOCOL_VERSION,
            "tool", service.info().tool(),
            "version", service.info().version(),
            "methods", methods.toList()));

    try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      String line;
      while (!stopRequested && (line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          handle(line);
        }
      }
    } finally {
      endSessions(awaitInFlight());
      standalone.shutdownNow();
    }
  }

  /**
   * Lets accepted requests send their final event, unless they take longer than the drain timeout.
   *
   * @return whether every accepted request ended in time
   */
  private boolean awaitInFlight() {
    var phase = inFlight.arriveAndDeregister();
    try {
      inFlight.awaitAdvanceInterruptibly(phase, drainTimeout.toMillis(), TimeUnit.MILLISECONDS);
      return true;
    } catch (TimeoutException _) {
      System.err.println(
          service.info().tool()
              + ": requests still running after "
              + drainTimeout.toSeconds()
              + " s were interrupted");
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
    return false;
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
  private void closeTracked(SessionWorker<S> worker, Runnable afterwards) {
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

  /**
   * Ends the sessions that are still open. After a complete drain no command is running, so each
   * session is closed on its own queue. After a timeout a command may still be using its session,
   * so the workers are only interrupted and the sessions are left to the process exit.
   */
  private void endSessions(boolean drained) {
    var workers = Stream.concat(sessions.values().stream(), connecting.values().stream()).toList();
    sessions.clear();
    connecting.clear();
    if (drained) {
      closeAndAwait(workers);
    }
    workers.forEach(SessionWorker::abandon);
  }

  /** Closes the sessions of {@code workers}, waiting for them no longer than the drain timeout. */
  private void closeAndAwait(List<SessionWorker<S>> workers) {
    var closed = new CountDownLatch(workers.size());
    workers.forEach(worker -> worker.closeAfterPending(closed::countDown));
    try {
      if (!closed.await(drainTimeout.toMillis(), TimeUnit.MILLISECONDS)) {
        System.err.println(
            service.info().tool()
                + ": sessions still closing after "
                + drainTimeout.toSeconds()
                + " s were interrupted");
      }
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
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
      switch (request.method()) {
        case CONNECT -> connect(request.params(), reply);
        case DISCONNECT -> disconnect(request.params(), reply);
        case SHUTDOWN -> {
          reply.succeed();
          stopRequested = true;
        }
        default -> runCommand(request, reply);
      }
    } catch (RuntimeException failure) {
      reply.fail(toError(failure));
    }
  }

  /**
   * {@code connect} is the first command of a session: the service logs in and reads the device,
   * then the server answers with the session id and what the service returned. If that fails, the
   * answer is the error any command would give and no session exists. The session id is usable only
   * after the reply.
   */
  private void connect(RequestParams params, Reply reply) {
    var worker = new SessionWorker<>("s" + sessionCounter.incrementAndGet(), service.open(params));
    connecting.put(worker.id(), worker);
    // The reply may be sent later, once the session has been closed, so it need not be finished
    // yet.
    runTracked(
        worker::submit, () -> guarded(reply, () -> finishConnect(worker, params, reply), false));
  }

  private void finishConnect(SessionWorker<S> worker, RequestParams params, Reply reply) {
    Establishment establishment;
    try {
      establishment = service.establish(worker.session(), params, reply);
    } catch (RuntimeException failure) {
      establishment = new Establishment.Rejected(toError(failure));
    }
    switch (establishment) {
      case Establishment.Established(var data) -> {
        connecting.remove(worker.id());
        sessions.put(worker.id(), worker);
        var result = Json.object("session", worker.id());
        result.putAll(data);
        reply.result(CONNECT, result, null);
        reply.succeed();
      }
      case Establishment.Rejected(var error) -> rejectConnection(worker, reply, error);
    }
  }

  /** A failed connect leaves no session behind: the login and the stored password are discarded. */
  private void rejectConnection(SessionWorker<S> worker, Reply reply, ServiceError error) {
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

  private SessionWorker<S> workerFor(String sessionId) {
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
    throw new RequestRejectedException(new ServiceError(CoreErrorCode.UNKNOWN_SESSION, message));
  }

  private void runCommand(Request request, Reply reply) {
    var spec = commands.get(request.method());
    if (spec == null) {
      throw new RequestRejectedException(
          new ServiceError(
              CoreErrorCode.UNKNOWN_METHOD, "unknown method '" + request.method() + "'"));
    }
    switch (spec) {
      case CommandSpec.SessionScoped<S> scoped -> {
        var command = scoped.factory().apply(request.params());
        var worker = workerFor(request.params().requireString("session"));
        runTracked(
            worker::submit, () -> guarded(reply, () -> command.run(worker.session(), reply)));
      }
      case CommandSpec.Standalone<S> unscoped -> {
        var command = unscoped.factory().apply(request.params());
        runTracked(standalone, () -> guarded(reply, () -> command.run(reply)));
      }
    }
  }
}
