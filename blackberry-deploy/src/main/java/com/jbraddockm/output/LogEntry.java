package com.jbraddockm.output;

import java.util.Optional;

/**
 * A message the tool printed for humans.
 *
 * @param level the severity
 * @param message the text without the {@code Info: } prefix and without trailing padding
 */
public record LogEntry(LogLevel level, String message) {

  /**
   * Recognises an informational line.
   *
   * <p>Lines starting with {@code Info: } are messages. A message beginning with {@code [ERROR]} is
   * reported as {@link LogLevel#WARN}, because the tool uses it for problems that do not stop the
   * command. The device pads some lines with trailing spaces; those are removed.
   *
   * @param line one line of the tool's output
   * @return the entry, or empty if the line is not an informational message
   */
  public static Optional<LogEntry> fromOutputLine(String line) {
    if (!line.startsWith(OutputText.INFO_PREFIX)) {
      return Optional.empty();
    }
    var message = line.substring(OutputText.INFO_PREFIX.length()).stripTrailing();
    return Optional.of(
        new LogEntry(message.startsWith("[ERROR]") ? LogLevel.WARN : LogLevel.INFO, message));
  }
}
