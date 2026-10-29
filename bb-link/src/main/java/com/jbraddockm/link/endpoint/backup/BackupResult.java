package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.SequencedMap;

/**
 * What a backup produced.
 *
 * @param file the {@code .bbb} file that was written
 * @param totalBytes the total number of archive bytes written across all categories
 * @param pin the device PIN the backup was taken from, or {@code null} if it was not recorded
 * @param softwareVersion the device software version, or {@code null} if it was not recorded
 * @param categoryBytes the archive byte count of each category, by its wire name, in the order
 *     they were written
 */
public record BackupResult(
    Path file,
    long totalBytes,
    String pin,
    String softwareVersion,
    SequencedMap<String, Long> categoryBytes)
    implements CommandResult {

  /** Takes an immutable copy of the per-category sizes. */
  public BackupResult {
    Objects.requireNonNull(file, "file");
    categoryBytes = java.util.Collections.unmodifiableSequencedMap(new java.util.LinkedHashMap<>(categoryBytes));
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "file",
        file.toString(),
        "categories",
        categoryBytes.entrySet().stream()
            .map(entry -> (Object) Json.object("category", entry.getKey(), "bytes", entry.getValue()))
            .toList(),
        "totalBytes",
        totalBytes,
        "pin",
        pin,
        "softwareVersion",
        softwareVersion);
  }
}
