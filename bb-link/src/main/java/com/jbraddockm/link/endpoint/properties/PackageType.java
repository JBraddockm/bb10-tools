package com.jbraddockm.link.endpoint.properties;

import java.util.Arrays;

/** The kind of an installed package, as the device classifies it. */
public enum PackageType {

  /** An ordinary application, which can be uninstalled. */
  APPLICATION("application", "application"),

  /** The core operating system package. */
  OPERATING_SYSTEM("os", "operatingSystem"),

  /** The radio (modem) package. */
  RADIO("radio", "radio"),

  /** An entry without a type: a removable or data package. */
  UNCLASSIFIED("", "unclassified"),

  /** A type this version does not know. */
  UNKNOWN("?", "unknown");

  private final String deviceText;
  private final String jsonName;

  PackageType(String deviceText, String jsonName) {
    this.deviceText = deviceText;
    this.jsonName = jsonName;
  }

  /**
   * Recognises the device's type text.
   *
   * @param text the content of the {@code Type} element; may be empty
   * @return the matching type, or {@link #UNKNOWN}
   */
  public static PackageType of(String text) {
    return Arrays.stream(values())
        .filter(type -> type != UNKNOWN && type.deviceText.equals(text))
        .findFirst()
        .orElse(UNKNOWN);
  }

  /**
   * Returns the name used in the wire protocol.
   *
   * @return for example {@code operatingSystem}
   */
  public String jsonName() {
    return jsonName;
  }
}
