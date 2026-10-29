package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the backup conversation streamed from the device: one file per category, and the total size
 * the device reported. The orchestrator turns these files into a {@code .bbb} archive.
 *
 * @param categories the categories that were downloaded, in the order they were sent
 * @param totalSize the total number of bytes the device reported it would send, or {@code 0} if it
 *     gave none
 * @param answer the device's last answer, kept whole
 */
record BackupStreamResult(List<DownloadedCategory> categories, long totalSize, ResponseBody answer)
    implements CommandResult {

  /** Takes an immutable copy of the list. */
  BackupStreamResult {
    categories = List.copyOf(categories);
    Objects.requireNonNull(answer, "answer");
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "categories",
        categories.stream()
            .map(
                entry ->
                    (Object)
                        Json.object(
                            "category", entry.category().wireName(), "bytes", entry.bytes()))
            .toList(),
        "totalSize",
        totalSize);
  }
}
