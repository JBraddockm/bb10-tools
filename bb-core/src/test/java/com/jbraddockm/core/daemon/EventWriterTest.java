package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link EventWriter}: one flushed UTF-8 line per event, intact under concurrent writers.
 */
@DisplayName("EventWriter: the lines of the protocol")
class EventWriterTest {

  /** An output stream that counts its flushes. */
  private static final class FlushCounting extends ByteArrayOutputStream {
    private int flushes;

    @Override
    public synchronized void flush() {
      flushes++;
    }

    synchronized int flushes() {
      return flushes;
    }
  }

  @Test
  @DisplayName(
      "writes the event as one line of compact JSON, with a line feed, in UTF-8, so that a"
          + " character above 127 is the bytes of its code point")
  void oneLinePerEvent() {
    var output = new ByteArrayOutputStream();

    new EventWriter(new PrintStream(output, false, StandardCharsets.UTF_8))
        .write(Json.object("type", "x", "text", "é😀", "list", List.of(1, 2)));

    assertThat(output.toString(StandardCharsets.UTF_8))
        .isEqualTo(Resources.text(EventWriterTest.class, "event-writer--event.txt"));
    assertThat(output.toByteArray()).contains("😀".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  @DisplayName("flushes after each event, so that nothing stays in a buffer while the client waits")
  void flushesEachEvent() {
    var output = new FlushCounting();
    var writer = new EventWriter(new PrintStream(output, false, StandardCharsets.UTF_8));

    writer.write(Json.object("type", "a"));
    writer.write(Json.object("type", "b"));

    assertThat(output.flushes()).isEqualTo(2);
  }

  @Test
  @DisplayName(
      "keeps the lines of eight threads that write at once whole and apart: every line is an event"
          + " of its own")
  void threadsDoNotInterleave() throws Exception {
    var output = new ByteArrayOutputStream();
    var writer = new EventWriter(new PrintStream(output, false, StandardCharsets.UTF_8));
    try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
      IntStream.range(0, 400)
          .forEach(
              number ->
                  pool.submit(
                      () ->
                          writer.write(
                              Json.object(
                                  "type", "x", "number", number, "padding", "p".repeat(200)))));
      pool.shutdown();
      assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
    }

    var lines = output.toString(StandardCharsets.UTF_8).lines().toList();

    assertThat(lines).hasSize(400);
    assertThat(lines).allSatisfy(line -> assertThat(Json.parse(line)).isNotNull());
    assertThat(
            lines.stream()
                .map(line -> ((Number) ((Map<?, ?>) Json.parse(line)).get("number")).intValue())
                .distinct())
        .hasSize(400);
  }
}
