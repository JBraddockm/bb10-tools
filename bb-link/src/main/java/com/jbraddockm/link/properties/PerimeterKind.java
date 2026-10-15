package com.jbraddockm.link.properties;

/** The kind of a perimeter, a separated area of the device with its own owner. */
public enum PerimeterKind {

  /** The owner's personal area. */
  PERSONAL("personal"),

  /** An area managed by an employer. */
  WORK("work"),

  /** A kind this version does not know. */
  UNKNOWN("unknown");

  private final String jsonName;

  PerimeterKind(String jsonName) {
    this.jsonName = jsonName;
  }

  /**
   * Recognises the device's description of a perimeter.
   *
   * @param text the content of {@code PerimeterType}, or the element name such as {@code
   *     PersonalPerimeter} if the type is missing
   * @return the kind; {@link #UNKNOWN} if the text matches neither
   */
  public static PerimeterKind of(String text) {
    var lower = text.toLowerCase(java.util.Locale.ROOT);
    if (lower.startsWith("personal")) {
      return PERSONAL;
    }
    return lower.startsWith("work") ? WORK : UNKNOWN;
  }

  /**
   * Returns the name used in the wire protocol.
   *
   * @return for example {@code personal}
   */
  public String jsonName() {
    return jsonName;
  }
}
