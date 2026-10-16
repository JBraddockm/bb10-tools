package com.jbraddockm.core.bar;

/** What a bar contains, as far as installing it is concerned. */
public enum BarKind {

  /** An application. */
  APPLICATION("application"),

  /** The operating system. */
  OPERATING_SYSTEM("operatingSystem"),

  /** The radio firmware. */
  RADIO("radio"),

  /** Any other package type: debug tokens, device configuration, system data, other patches. */
  OTHER("other");

  private final String wireName;

  BarKind(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the name used in the wire protocol.
   *
   * @return for example {@code operatingSystem}
   */
  public String wireName() {
    return wireName;
  }

  /**
   * Tells whether this kind is firmware, which the device installs differently from an application
   * and which ends in a restart.
   *
   * @return {@code true} for the operating system and the radio
   */
  public boolean isFirmware() {
    return this == OPERATING_SYSTEM || this == RADIO;
  }
}
