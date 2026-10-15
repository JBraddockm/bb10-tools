package com.jbraddockm.link.properties;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A package installed on the device.
 *
 * @param name the full package name
 * @param type how the device classifies the package
 * @param packageId the handle used to uninstall the package; may be {@code null}
 * @param versionId the device's identifier of this version; may be {@code null}
 * @param version the version text; may be {@code null}
 * @param fingerprint the SHA-512 checksum of the package; may be {@code null}
 */
public record InstalledPackage(
    String name,
    PackageType type,
    String packageId,
    String versionId,
    String version,
    String fingerprint) {

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map; absent values are {@code null}
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "name", name,
        "type", type.jsonName(),
        "packageId", packageId,
        "versionId", versionId,
        "version", version,
        "fingerprint", fingerprint);
  }
}
