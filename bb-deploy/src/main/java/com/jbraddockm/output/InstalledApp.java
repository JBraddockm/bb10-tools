package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.List;
import java.util.Map;

/**
 * An application installed on the device.
 *
 * @param fullName the package name, a dot, and the package id with each {@code -} written as {@code
 *     .}
 * @param name the package name; use it for {@code -package-name}; {@code null} if it cannot be
 *     derived
 * @param id the real package id, with {@code -}; use it for {@code -package-id}
 * @param version the version string
 * @param sizeBytes the installed size, or {@code null} if the device reported none that is numeric
 * @param attributes {@code key::value} extras such as {@code source} or {@code perimeter_guid}
 * @param flags bare-word extras such as {@code websl}
 * @param unknownFields two fields of the device's line whose meaning is not identified, kept so
 *     nothing is lost
 */
public record InstalledApp(
    String fullName,
    String name,
    String id,
    String version,
    Long sizeBytes,
    Map<String, String> attributes,
    List<String> flags,
    List<String> unknownFields)
    implements CommandResult {

  /** Takes immutable, order-preserving copies of the collections. */
  public InstalledApp {
    attributes = OutputText.orderedCopy(attributes);
    flags = List.copyOf(flags);
    unknownFields = List.copyOf(unknownFields);
  }

  /**
   * Tells whether the app was sideloaded for development.
   *
   * @return {@code true} if the {@code source} attribute is {@code developer}
   */
  public boolean isDeveloperApp() {
    return "developer".equals(attributes.get("source"));
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "fullName", fullName,
        "name", name,
        "id", id,
        "version", version,
        "sizeBytes", sizeBytes,
        "attributes", attributes,
        "flags", flags,
        "isDeveloperApp", isDeveloperApp(),
        "unknownFields", unknownFields);
  }
}
