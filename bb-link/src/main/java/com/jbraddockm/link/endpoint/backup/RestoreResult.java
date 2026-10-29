package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * What a restore did.
 *
 * @param restored the categories the device accepted, in the order they were uploaded
 * @param resetRequired whether the device reported it must reset to apply the restore
 */
public record RestoreResult(List<BackupCategory> restored, boolean resetRequired)
    implements CommandResult {

  /** Takes an immutable copy of the list. */
  public RestoreResult {
    restored = List.copyOf(restored);
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "restored", restored.stream().map(BackupCategory::wireName).toList(),
        "resetRequired", resetRequired);
  }
}
