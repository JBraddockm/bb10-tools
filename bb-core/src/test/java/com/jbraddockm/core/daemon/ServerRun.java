package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.JsonObjects;
import com.jbraddockm.core.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Runs a {@link DaemonServer} on a background thread over pipes, sends requests from templates and
 * collects the events it writes.
 */
final class ServerRun<S extends ServiceSession> implements AutoCloseable {

  private static final Duration PATIENCE = Duration.ofSeconds(10);

  /** Collects every line written to it, parsed as JSON. */
  private static final class Sink extends ByteArrayOutputStream {
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final StringBuilder partial = new StringBuilder();

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      partial.append(new String(bytes, offset, length, StandardCharsets.UTF_8));
      int newline;
      while ((newline = partial.indexOf("\n")) >= 0) {
        events.add(JsonObjects.object(Json.parse(partial.substring(0, newline))));
        partial.delete(0, newline + 1);
      }
      notifyAll();
    }

    synchronized List<Map<String, Object>> snapshot() {
      return List.copyOf(events);
    }

    synchronized boolean await(Predicate<List<Map<String, Object>>> condition, Duration time)
        throws InterruptedException {
      var deadline = System.nanoTime() + time.toNanos();
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

  private final Sink sink = new Sink();
  private final PipedOutputStream toServer = new PipedOutputStream();
  private final Thread server;
  private volatile IOException failure;

  ServerRun(Service<S> service, Duration drainTimeout) throws IOException {
    var fromClient = new PipedInputStream(toServer, 1 << 16);
    server =
        Thread.ofPlatform()
            .daemon()
            .start(
                () -> {
                  try {
                    DaemonServer.serve(service, fromClient, sink, drainTimeout);
                  } catch (IOException problem) {
                    failure = problem;
                  }
                });
  }

  Map<String, Object> hello() throws InterruptedException {
    assertThat(sink.await(events -> !events.isEmpty(), PATIENCE))
        .as("the server says hello")
        .isTrue();
    return sink.snapshot().getFirst();
  }

  /** Sends the request of a template. The identifier is JSON: a number, or a quoted text. */
  void send(String id, String resource, Map<String, String> values) {
    var all = new LinkedHashMap<>(values);
    all.put("id", id);
    write(Resources.text(ServerRun.class, resource, all).strip());
  }

  void send(String id, String resource) {
    send(id, resource, Map.of());
  }

  /** Writes the lines in a single write, so that the server reads them together. */
  void write(String... lines) {
    try {
      toServer.write((String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
      toServer.flush();
    } catch (IOException problem) {
      throw new UncheckedIOException(problem);
    }
  }

  static String line(String resource) {
    return Resources.text(ServerRun.class, resource).strip();
  }

  /** Waits for the terminal event of a request and returns it. */
  Map<String, Object> done(Object id) throws InterruptedException {
    assertThat(sink.await(events -> events.stream().anyMatch(event -> isDone(event, id)), PATIENCE))
        .as("done for " + id)
        .isTrue();
    return sink.snapshot().stream().filter(event -> isDone(event, id)).findFirst().orElseThrow();
  }

  /**
   * Returns whether the terminal event of a request arrives within the given time. Used to show
   * that it does not.
   */
  boolean doneWithin(Object id, Duration time) throws InterruptedException {
    return sink.await(events -> events.stream().anyMatch(event -> isDone(event, id)), time);
  }

  private static boolean isDone(Map<String, Object> event, Object id) {
    return "done".equals(event.get("type")) && Objects.equals(id, event.get("id"));
  }

  List<String> typesOf(Object id) {
    return sink.snapshot().stream()
        .filter(event -> Objects.equals(id, event.get("id")))
        .map(event -> (String) event.get("type"))
        .toList();
  }

  Map<String, Object> eventOf(Object id, String type) {
    return sink.snapshot().stream()
        .filter(event -> Objects.equals(id, event.get("id")) && type.equals(event.get("type")))
        .findFirst()
        .orElseThrow();
  }

  List<Map<String, Object>> events() {
    return sink.snapshot();
  }

  static Object errorCode(Map<String, Object> done) {
    return done.get("error") instanceof Map<?, ?> error ? error.get("code") : null;
  }

  static String errorMessage(Map<String, Object> done) {
    return String.valueOf(JsonObjects.object(done.get("error")).get("message"));
  }

  /** Closes the input of the server, as a client does when it goes away. */
  void endInput() throws IOException {
    toServer.close();
  }

  /** Returns whether the server stops within the given time. */
  boolean stoppedWithin(Duration time) throws InterruptedException {
    server.join(time);
    return !server.isAlive();
  }

  /** Returns the I/O error that stopped the server, or {@code null}. */
  IOException failure() {
    return failure;
  }

  @Override
  public void close() throws IOException {
    toServer.close();
  }
}
