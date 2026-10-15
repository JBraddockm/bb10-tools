package com.jbraddockm.link.discovery;

import java.util.Arrays;

/** How a discovered device is connected. */
public enum ConnectionKind {

  /** Reached over the network. */
  WIFI("WiFi"),

  /** Reached over USB networking, in the link-local range {@code 169.254.0.0/16}. */
  USB("USB"),

  /** A simulator. */
  SIMULATOR("Simulator");

  private final String wireName;

  ConnectionKind(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the name used in the wire protocol, which is the original tool's spelling.
   *
   * @return {@code WiFi}, {@code USB} or {@code Simulator}
   */
  public String wireName() {
    return wireName;
  }

  /**
   * Recognises the type text of the original detector.
   *
   * @param text the result of the detector's type method
   * @return the kind
   * @throws IllegalArgumentException if the text is not one of the three known spellings
   */
  public static ConnectionKind fromDetectorText(String text) {
    return Arrays.stream(values())
        .filter(kind -> kind.wireName.equals(text))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("unknown connection type: " + text));
  }
}
