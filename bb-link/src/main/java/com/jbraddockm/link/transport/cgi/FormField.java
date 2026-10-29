package com.jbraddockm.link.transport.cgi;

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

  /**
   * The computer's ecoid, sent at pairing (equal to {@link #DEVICE_ID}). The device folds the
   * stored ecoid into the mDNS Friendly-name match, so setting it here is what lets the device map
   * our advert to this pairing and show our name instead of the {@code Desktop-XXXX} fallback.
   */
  ECOID("ecoid"),

  /** The certificate of the computer's authority, as text. */
  CERT_DATA("certdata"),

  /** The package to uninstall. */
  PACKAGE_ID("packageid"),

  /** The end of an update or backup conversation. */
  STATUS("status"),

  /** What a backup conversation is asked for: the category list or the progress of preparing. */
  QUERY("query"),

  /** A backup option, such as {@code rev2} for the richer category list. */
  OPT("opt"),

  /** The total number of bytes a restore will upload across all categories. */
  TOTAL_SIZE("totalsize"),

  /** The package types to sub-select within the app category on a rev2 backup. */
  PKGTYPE("pkgtype"),

  /** The operation a PIM write performs, when it is carried in the body rather than the query. */
  COMMAND("command"),

  /** The kind of items a PIM write works on. */
  SERVICE("service"),

  /** The client the PIM write is for. */
  WORKSTATION_ID("workstation_id"),

  /**
   * A PIM item as its own JSON, forwarded to the device verbatim. The CGI reads it from this {@code
   * data} field and wraps it as the backend's {@code json=} body itself.
   */
  DATA("data");

  private final String wireName;

  FormField(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
