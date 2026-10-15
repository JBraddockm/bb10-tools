package com.jbraddockm.core.daemon;

import com.jbraddockm.core.json.Json;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * The only writer to the real standard output: one JSON object per line, each ended by {@code \n}.
 */
final class EventWriter {

  private final PrintStream stream;

  EventWriter(PrintStream stream) {
    this.stream = stream;
  }

  /**
   * Writes one event.
   *
   * <p>Each event is a single write, so a process exit can never leave half a line behind, and the
   * line terminator is always {@code \n}, whatever the platform.
   */
  synchronized void write(Map<String, Object> event) {
    var bytes = (Json.write(event) + "\n").getBytes(StandardCharsets.UTF_8);
    stream.write(bytes, 0, bytes.length);
    stream.flush();
  }
}
