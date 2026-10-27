package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.link.transport.xml.XmlElement;
import java.util.List;
import java.util.Optional;

/**
 * Reads the {@code DynamicProperties} element as real devices write it: packages under {@code
 * Applications}, removed packages under {@code RemovedApplications}, and the rest grouped under
 * {@code DeviceStatus}, {@code DeviceHardware}, {@code DeviceInformation}, {@code Radio} and {@code
 * Perimeters}. Elements this version does not know are ignored.
 */
final class DevicePropertiesParser {

  private DevicePropertiesParser() {}

  /**
   * Reads the element.
   *
   * @param element the {@code DynamicProperties} element
   * @return the properties, or empty if the element has no content at all
   */
  static Optional<DeviceProperties> parse(XmlElement element) {
    if (element.children().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new DeviceProperties(
            element.textAt("DeviceInformation", "DeviceName").orElse(null),
            element.textAt("DeviceInformation", "ModelName").orElse(null),
            element.textAt("DeviceHardware", "HardwareID").orElse(null),
            element.textAt("Radio", "Imei").orElse(null),
            element
                .textAt("DeviceStatus", "BatteryLevel")
                .flatMap(DevicePropertiesParser::integer)
                .orElse(null),
            element
                .textAt("DeviceStatus", "BatteryStateOfHealth")
                .flatMap(DevicePropertiesParser::integer)
                .orElse(null),
            element.textAt("DeviceStatus", "HasPower").map(Boolean::parseBoolean).orElse(null),
            element
                .textAt("DeviceStatus", "FreeApplicationSpace")
                .flatMap(DevicePropertiesParser::number)
                .orElse(null),
            element.textAt("DeviceStatus", "BoardSerialNumber").orElse(null),
            element.textAt("DeviceInformation", "RefurbDate").orElse(null),
            element.childText("PolicyRestrictions").orElse(null),
            perimeters(element),
            applications(element, "Applications"),
            applications(element, "RemovedApplications")));
  }

  private static List<Perimeter> perimeters(XmlElement element) {
    return element
        .child("Perimeters")
        .map(
            perimeters ->
                perimeters.children().stream()
                    .map(
                        perimeter ->
                            new Perimeter(
                                PerimeterKind.of(
                                    perimeter.childText("PerimeterType").orElse(perimeter.name())),
                                perimeter.childText("Bbid").orElse(null)))
                    .toList())
        .orElse(List.of());
  }

  private static List<InstalledPackage> applications(XmlElement element, String container) {
    return element
        .child(container)
        .map(
            applications ->
                applications.children("Application").stream()
                    .map(DevicePropertiesParser::application)
                    .toList())
        .orElse(List.of());
  }

  private static InstalledPackage application(XmlElement application) {
    return new InstalledPackage(
        application.childText("Name").orElse(null),
        PackageType.of(application.child("Type").map(XmlElement::text).orElse("")),
        application.childText("PackageId").orElse(null),
        application.childText("PackageVersionId").orElse(null),
        application.childText("PackageVersion").orElse(null),
        application.childText("Fingerprint").orElse(null));
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.valueOf(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static Optional<Long> number(String text) {
    try {
      return Optional.of(Long.valueOf(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
