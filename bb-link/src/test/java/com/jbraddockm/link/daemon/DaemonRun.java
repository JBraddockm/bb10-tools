package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.JsonObjects;
import com.jbraddockm.link.testing.Resources;
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
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/**
 * Runs the daemon on a background thread over pipes, sends requests from templates and collects the
 * events it writes.
 */
final class DaemonRun implements AutoCloseable {

  private static final Duration PATIENCE = Duration.ofSeconds(10);

  /** Collects every line written to it, parsed as JSON, and the output as a whole text. */
  private static final class Sink extends ByteArrayOutputStream {
    private final List<Map<String, Object>> events = new ArrayList<>();
    private final StringBuilder everything = new StringBuilder();
    private final StringBuilder partial = new StringBuilder();

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      var text = new String(bytes, offset, length, StandardCharsets.UTF_8);
      everything.append(text);
      partial.append(text);
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

    synchronized String text() {
      return everything.toString();
    }

    synchronized boolean await(Predicate<List<Map<String, Object>>> condition)
        throws InterruptedException {
      var deadline = System.nanoTime() + PATIENCE.toNanos();
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

  DaemonRun(LinkService service) throws IOException {
    var fromClient = new PipedInputStream(toServer, 1 << 16);
    server =
        Thread.ofPlatform()
            .daemon()
            .start(
                () -> {
                  try {
                    DaemonServer.serve(service, fromClient, sink, Duration.ofSeconds(10));
                  } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                  }
                });
  }

  /** Waits for the greeting that the daemon writes first and returns it. */
  Map<String, Object> hello() throws InterruptedException {
    assertThat(sink.await(events -> !events.isEmpty())).as("the daemon says hello").isTrue();
    return sink.snapshot().getFirst();
  }

  /** Sends the request of a template, with the given identifier and placeholder values. */
  void send(long id, String resource, Map<String, String> values) {
    var all = new LinkedHashMap<>(values);
    all.put("id", String.valueOf(id));
    var line = Resources.text(DaemonRun.class, resource, all).strip();
    try {
      toServer.write((line + "\n").getBytes(StandardCharsets.UTF_8));
      toServer.flush();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /** Sends the request of a template that has no placeholder but the identifier. */
  void send(long id, String resource) {
    send(id, resource, Map.of());
  }

  /** Waits for the terminal event of a request and returns it. */
  Map<String, Object> done(long id) throws InterruptedException {
    assertThat(sink.await(events -> events.stream().anyMatch(event -> isDone(event, id))))
        .as("done for " + id)
        .isTrue();
    return sink.snapshot().stream().filter(event -> isDone(event, id)).findFirst().orElseThrow();
  }

  private static boolean isDone(Map<String, Object> event, long id) {
    return "done".equals(event.get("type")) && Long.valueOf(id).equals(event.get("id"));
  }

  /** Returns the events of a request that have the given type; {@code *} matches every type. */
  List<Map<String, Object>> events(long id, String type) {
    return sink.snapshot().stream()
        .filter(
            event ->
                ("*".equals(type) || type.equals(event.get("type")))
                    && Long.valueOf(id).equals(event.get("id")))
        .toList();
  }

  /** Returns the data of the first result of a request. */
  Map<String, Object> data(long id) {
    return JsonObjects.object(events(id, "result").getFirst().get("data"));
  }

  /** Returns the session created by a successful connect. */
  String session(long id) {
    return String.valueOf(data(id).get("session"));
  }

  static Object errorCode(Map<String, Object> done) {
    return done.get("error") instanceof Map<?, ?> error ? error.get("code") : null;
  }

  static Map<String, Object> errorDetails(Map<String, Object> done) {
    return JsonObjects.object(JsonObjects.object(done.get("error")).get("details"));
  }

  static String errorMessage(Map<String, Object> done) {
    return String.valueOf(JsonObjects.object(done.get("error")).get("message"));
  }

  String output() {
    return sink.text();
  }

  /** Closes the input of the daemon, as a client does when it goes away. */
  void endInput() throws IOException {
    toServer.close();
  }

  /** Returns whether the daemon stops within the time a test may wait. */
  boolean stopped() throws InterruptedException {
    server.join(PATIENCE);
    return !server.isAlive();
  }

  @Override
  public void close() throws IOException {
    toServer.close();
  }
}
