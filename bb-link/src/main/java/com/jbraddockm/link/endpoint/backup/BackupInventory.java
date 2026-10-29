package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The categories the device offers to back up, with their sizes.
 *
 * @param categories the categories, in the order the device listed them
 */
record BackupInventory(List<AvailableCategory> categories) implements CommandResult {

  /** Takes an immutable copy of the list. */
  BackupInventory {
    categories = List.copyOf(categories);
  }

  /**
   * Finds a category in the inventory.
   *
   * @param category the category to look for
   * @return what the device said about it, or empty if it was not listed
   */
  public Optional<AvailableCategory> find(BackupCategory category) {
    return categories.stream().filter(entry -> entry.category() == category).findFirst();
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "categories", categories.stream().map(entry -> (Object) entry.toJson()).toList());
  }
}
