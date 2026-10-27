package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A package that the device reported installed.
 *
 * @param file the bar that was sent
 * @param packageId the package's id; may be {@code null}
 * @param packageName the package's name; may be {@code null}
 * @param packageVersion the package's version; may be {@code null}
 * @param sizeBytes the size of the bar
 */
record InstalledPackage(
    String file, String packageId, String packageName, String packageVersion, long sizeBytes) {

  /**
   * Describes a bar that was installed.
   *
   * @param bar what its manifest said
   * @return the description
   */
  public static InstalledPackage of(BarInfo bar) {
    return new InstalledPackage(
        bar.file().toString(),
        bar.packageId(),
        bar.packageName(),
        bar.packageVersion(),
        bar.sizeBytes());
  }

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "file", file,
        "packageId", packageId,
        "packageName", packageName,
        "packageVersion", packageVersion,
        "sizeBytes", sizeBytes);
  }
}
