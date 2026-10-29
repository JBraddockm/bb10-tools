package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.Objects;

/**
 * One category the device offers to back up, as read from the category list.
 *
 * @param category the category
 * @param bytesize the number of bytes the device reports for it, or {@code 0} if it gave none
 * @param count the number of items the device reports for it, or {@code 0} if it gave none
 * @param ready whether the device marks the category ready to back up ({@code readystate=1})
 * @param perimeterType the perimeter the device reports (such as {@code personal}), or {@code null}
 */
record AvailableCategory(
    BackupCategory category, long bytesize, int count, boolean ready, String perimeterType) {

  /**
   * Validates the category.
   *
   * @throws NullPointerException if the category is {@code null}
   */
  AvailableCategory {
    Objects.requireNonNull(category, "category");
  }

  /**
   * Returns the wire fields of this category.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "category", category.wireName(),
        "bytesize", bytesize,
        "count", count,
        "ready", ready,
        "perimeterType", perimeterType);
  }
}
