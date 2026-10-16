package com.jbraddockm.core.bar;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * What the manifest of a bar says about its package.
 *
 * @param file the bar
 * @param sizeBytes the size of the file, which is what the device is told before the upload
 * @param kind what the bar contains
 * @param packageType the {@code Package-Type} of the manifest, for example {@code application}
 * @param packageId the {@code Package-Id}, which names the package on the device; may be {@code
 *     null}
 * @param packageName the {@code Package-Name}; may be {@code null}
 * @param packageVersion the {@code Package-Version}; may be {@code null}
 * @param architecture the {@code Package-Architecture}, for example {@code armle-v7}; may be {@code
 *     null}
 * @param manifestVersion the {@code Archive-Manifest-Version}, the version of the manifest format;
 *     may be {@code null}
 * @param developmentMode whether the manifest says {@code Application-Development-Mode: true},
 *     which marks a bar built for a device in development mode
 * @param signed whether the manifest carries the attributes that signing adds. The signature itself
 *     is not checked.
 */
public record BarInfo(
    Path file,
    long sizeBytes,
    BarKind kind,
    String packageType,
    String packageId,
    String packageName,
    String packageVersion,
    String architecture,
    String manifestVersion,
    boolean developmentMode,
    boolean signed)
    implements CommandResult {

  /**
   * Validates the components that cannot be absent.
   *
   * @throws NullPointerException if the file, the kind or the package type is {@code null}
   */
  public BarInfo {
    Objects.requireNonNull(file, "file");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(packageType, "packageType");
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "file", file.toString(),
        "sizeBytes", sizeBytes,
        "kind", kind.wireName(),
        "packageType", packageType,
        "packageId", packageId,
        "packageName", packageName,
        "packageVersion", packageVersion,
        "architecture", architecture,
        "manifestVersion", manifestVersion,
        "developmentMode", developmentMode,
        "signed", signed);
  }
}
