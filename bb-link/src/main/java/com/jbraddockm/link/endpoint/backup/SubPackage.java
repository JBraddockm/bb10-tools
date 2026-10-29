package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.Objects;

/**
 * One sub-package the device lists under a category in a rev2 inventory — an individual app or an
 * app group, with its type.
 *
 * @param pkgid the device's package identifier
 * @param type the package type ({@code system}/{@code data}/{@code bin})
 * @param bytesize the number of bytes the device reports for it, or {@code 0} if it gave none
 * @param count the number of items the device reports for it, or {@code 0} if it gave none
 * @param name the display name, or empty
 */
record SubPackage(String pkgid, PackageType type, long bytesize, int count, String name) {

  /** Validates the sub-package. */
  SubPackage {
    Objects.requireNonNull(pkgid, "pkgid");
    Objects.requireNonNull(type, "type");
    name = name == null ? "" : name;
  }

  /**
   * Returns the wire fields of this sub-package.
   *
   * @return an insertion-ordered map
   */
  Map<String, Object> toJson() {
    return Json.object(
        "pkgid", pkgid,
        "type", type.wireName(),
        "bytesize", bytesize,
        "count", count,
        "name", name);
  }
}
