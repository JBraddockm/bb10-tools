package com.jbraddockm.link.endpoint.properties;

import java.util.Locale;
import java.util.Objects;

/** The kind of a perimeter, a separated area of the device with its own owner. */
enum PerimeterKind {

  /** The owner's personal area, which is also the default perimeter of a device without another. */
  PERSONAL("personal"),

  /** An area managed by an employer. */
  ENTERPRISE("enterprise"),

  /** A kind this version does not know. */
  UNKNOWN("unknown");

  private final String jsonName;

  PerimeterKind(String jsonName) {
    this.jsonName = jsonName;
  }

  /**
   * Recognises the device's description of a perimeter by the start of the word, in any case. The
   * platform names its perimeter types {@code Personal}, {@code Enterprise} and {@code Default}; a
   * default perimeter is the personal one on a device that has no enterprise perimeter.
   *
   * @param text the content of {@code PerimeterType}, or the element name such as {@code
   *     PersonalPerimeter} if the type is missing
   * @return the kind; {@link #UNKNOWN} if the text matches none of these
   * @throws NullPointerException if {@code text} is {@code null}
   */
  public static PerimeterKind of(String text) {
    var lower = Objects.requireNonNull(text, "text").toLowerCase(Locale.ROOT);
    if (lower.startsWith("personal") || lower.startsWith("default")) {
      return PERSONAL;
    }
    return lower.startsWith("enterprise") ? ENTERPRISE : UNKNOWN;
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
