package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.link.transport.cgi.WireValue;

/**
 * What an update conversation is about. The device's own names are misleading: {@code bar} installs
 * applications, {@code os} installs firmware, and {@code app} <em>un</em>installs applications.
 */
enum UpdateMode implements WireValue {

  /** Install applications. */
  INSTALL_APPLICATIONS("bar"),

  /** Install firmware: the operating system, and the radio with it. */
  INSTALL_OPERATING_SYSTEM("os"),

  /** Uninstall applications. */
  UNINSTALL_APPLICATIONS("app");

  private final String wireName;

  UpdateMode(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
