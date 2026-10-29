package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the restore conversation did: the categories the device accepted, and whether it reported
 * that a reset is required (which it always has, so far — the device resets to apply the restore).
 *
 * @param restored the categories the device accepted, in the order they were uploaded
 * @param resetRequired whether the device reported that it must reset to apply the restore
 * @param answer the device's last answer, kept whole
 */
record RestoreStreamResult(
    List<BackupCategory> restored, boolean resetRequired, ResponseBody answer)
    implements CommandResult {

  /** Takes an immutable copy of the list. */
  RestoreStreamResult {
    restored = List.copyOf(restored);
    Objects.requireNonNull(answer, "answer");
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "restored", restored.stream().map(BackupCategory::wireName).toList(),
        "resetRequired", resetRequired);
  }
}
