package com.jbraddockm.link.cgi;

/** The names of the form fields that requests carry. */
public enum FormField implements WireValue {

  /** Asks for the dynamic properties; its value repeats its own name. */
  GET_DYNAMIC_PROPERTIES("Get Dynamic Properties"),

  /** The action to perform. */
  ACTION("action"),

  /** A property name. */
  NAME("name"),

  /** A property value. */
  VALUE("value"),

  /** A wipe-endpoint command such as reading the PIN. */
  WIPE("wipe"),

  /** What an update conversation is about: installing applications or firmware, or uninstalling. */
  MODE("mode"),

  /** The number of bytes that an installation will upload, or {@code 0} for an uninstallation. */
  SIZE("size"),

  /** The kind of package in a step of an uninstallation. */
  TYPE("type"),

  /** The identifier that the computer gave itself, to pair with a device. */
  DEVICE_ID("deviceid"),

  /** The certificate of the computer's authority, as text. */
  CERT_DATA("certdata"),

  /** The package to uninstall. */
  PACKAGE_ID("packageid"),

  /** The end of an update conversation. */
  STATUS("status");

  private final String wireName;

  FormField(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
