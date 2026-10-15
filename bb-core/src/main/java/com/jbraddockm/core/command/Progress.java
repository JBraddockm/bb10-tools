package com.jbraddockm.core.command;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.Objects;

/**
 * A progress report from a running command.
 *
 * @param percent the completed share, from 0 to 100; reports may repeat a value
 * @param phase the part of the command that is progressing
 * @param bytesDone the bytes transferred so far, or {@code null} if not applicable
 * @param bytesTotal the bytes expected in total, or {@code null} if unknown
 * @param item the file, package or category being processed, or {@code null}
 */
public record Progress(
    int percent, ProgressPhase phase, Long bytesDone, Long bytesTotal, String item) {

  /**
   * Validates the report.
   *
   * @throws IllegalArgumentException if {@code percent} is outside 0 to 100
   * @throws NullPointerException if {@code phase} is {@code null}
   */
  public Progress {
    if (percent < 0 || percent > 100) {
      throw new IllegalArgumentException("percent out of range: " + percent);
    }
    Objects.requireNonNull(phase, "phase");
  }

  /**
   * Creates a report that carries only a percentage.
   *
   * @param percent the completed share, from 0 to 100
   * @param phase the part of the command that is progressing
   * @return the report
   */
  public static Progress ofPercent(int percent, ProgressPhase phase) {
    return new Progress(percent, phase, null, null, null);
  }

  /**
   * Returns the wire fields of this report. Fields without a value are {@code null}.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "percent",
        percent,
        "phase",
        phase.wireName(),
        "bytesDone",
        bytesDone,
        "bytesTotal",
        bytesTotal,
        "item",
        item);
  }
}
