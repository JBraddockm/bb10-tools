package com.jbraddockm.link.endpoint.characteristics;

import com.jbraddockm.link.transport.xml.XmlElement;
import java.util.Optional;

/**
 * Reads the {@code DeviceCharacteristics} element of a {@code discovery.cgi} response. Elements
 * this version does not know are ignored; the decimal {@code BbPin} is formatted as the hexadecimal
 * PIN.
 */
final class DeviceCharacteristicsParser {

  private DeviceCharacteristicsParser() {}

  /**
   * Reads the element.
   *
   * @param element the {@code DeviceCharacteristics} element
   * @return the characteristics, or empty if the element has no content at all
   */
  static Optional<DeviceCharacteristics> parse(XmlElement element) {
    if (element.children().isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new DeviceCharacteristics(
            element.childText("BbPin").flatMap(DeviceCharacteristicsParser::pin).orElse(null),
            element.childText("Guid").orElse(null),
            element.childText("ModelName").orElse(null),
            element.childText("OsType").orElse(null),
            element.childText("PlatformVersion").orElse(null),
            element.childText("IMEI").orElse(null),
            element.childText("Power").flatMap(DeviceCharacteristicsParser::integer).orElse(null),
            element.childText("DeveloperModeEnabled").map("1"::equals).orElse(null),
            element.childText("Bp2pTunnelStatus").map("1"::equals).orElse(null),
            element
                .childText("ScreenWidth")
                .flatMap(DeviceCharacteristicsParser::integer)
                .orElse(null),
            element
                .childText("ScreenHeight")
                .flatMap(DeviceCharacteristicsParser::integer)
                .orElse(null),
            element
                .childText("FlashSize")
                .flatMap(DeviceCharacteristicsParser::number)
                .orElse(null),
            element.childText("SDCard").flatMap(DeviceCharacteristicsParser::integer).orElse(null),
            element
                .childText("BlendPolicy")
                .flatMap(DeviceCharacteristicsParser::integer)
                .orElse(null),
            element.childText("FuseEnabled").map("1"::equals).orElse(null),
            element.childText("FuseInstalled").map("1"::equals).orElse(null)));
  }

  /** Formats the decimal {@code BbPin} as the 8-digit uppercase hex PIN (740193667 → 2C1E7583). */
  private static Optional<String> pin(String decimal) {
    try {
      return Optional.of(String.format("%08X", Long.parseLong(decimal.trim())));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.valueOf(text.trim()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  private static Optional<Long> number(String text) {
    try {
      return Optional.of(Long.valueOf(text.trim()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
