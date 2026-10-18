package com.jbraddockm.link.cgi;

/** The CGI programs of the device that bb-link talks to. */
public enum Endpoint {

  /** Device properties and installed packages. */
  DYNAMIC_PROPERTIES("dynamicProperties.cgi"),

  /** Installing and uninstalling packages and operating system images. */
  UPDATE("update.cgi"),

  /** Backup and restore. */
  BACKUP("backup.cgi"),

  /** Wipe, PIN and signing actions. Reading the PIN is among them. */
  WIPE("wipe.cgi"),

  /** Rebooting the device. */
  RESET("reset.cgi"),

  /** Support logs. */
  SUPPORT("support.cgi"),

  /**
   * The login, which is also said to sign a certificate request into a certificate for port 8443
   * **[RESEARCH]**.
   */
  LOGIN("login.cgi"),

  /**
   * Pairing: telling the device which computer it is paired with, so that it lists the computer in
   * Device Connections.
   */
  PAIRING("pairing.cgi"),

  /**
   * The synchronisation of personal information: contacts, calendars and events. Unlike the other
   * programs it answers with JSON-like text instead of XML.
   */
  PIM_SYNC("pimsync.cgi");

  private final String fileName;

  Endpoint(String fileName) {
    this.fileName = fileName;
  }

  /**
   * Returns the file name of the CGI program.
   *
   * @return for example {@code dynamicProperties.cgi}
   */
  public String fileName() {
    return fileName;
  }

  /**
   * Returns the URL path of the CGI program.
   *
   * @return for example {@code /cgi-bin/dynamicProperties.cgi}
   */
  public String path() {
    return "/cgi-bin/" + fileName;
  }
}
