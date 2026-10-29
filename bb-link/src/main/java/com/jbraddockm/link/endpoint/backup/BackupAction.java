package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.link.transport.cgi.WireValue;

/** The {@code action} a backup conversation names. Backup is the default and names no action. */
enum BackupAction implements WireValue {

  /** Restore a backup onto the device. The device spells it with a capital R. */
  RESTORE("Restore");

  private final String wireName;

  BackupAction(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
