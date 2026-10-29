package com.jbraddockm.link.transport.cgi;

import java.util.Arrays;
import java.util.Optional;

/**
 * The response elements this version of bb-link knows. The device names the kind of answer by the
 * first element inside its envelope.
 */
public enum ResponseElement {

  /** The device properties and installed packages. */
  DYNAMIC_PROPERTIES("DynamicProperties"),

  /** The device PIN. */
  DEVICE_PIN("DevicePIN"),

  /** The device characteristics from {@code discovery.cgi}. */
  DEVICE_CHARACTERISTICS("DeviceCharacteristics"),

  /** The device wants the client to authenticate again. */
  AUTH_CHALLENGE("AuthChallenge"),

  /** The device's verdict on an authentication. */
  AUTH("Auth"),

  /** An update conversation was accepted, and the device is ready for the first package. */
  UPDATE_START("UpdateStart"),

  /** The device has received a package. */
  UPDATE_SEND("UpdateSend"),

  /** How far the device is with installing a package. */
  UPDATE_PROGRESS("UpdateProgress"),

  /** The device has finished an update conversation. */
  UPDATE_END("UpdateEnd"),

  /** An uninstallation was accepted, and the device is ready for the first package id. */
  DELETE_REQUEST("DeleteRequest"),

  /** The device has removed a package. */
  DELETE_PROGRESS("DeleteProgress"),

  /** The categories available for backup, with their sizes. */
  BACKUP_LIST("BackupList"),

  /** A backup conversation was accepted, and the device is preparing the archive. */
  BACKUP_START("BackupStart"),

  /** How far the device is with preparing a backup; carries the total size when ready. */
  BACKUP_START_ACTIVITY("BackupStartActivity"),

  /** The device has finished a backup or restore conversation. */
  BACKUP_END("BackupEnd"),

  /** A restore conversation was accepted, and the device is ready for the first category. */
  RESTORE_START("RestoreStart"),

  /** The device has taken a restored category; carries whether a reset is required. */
  RESTORE_SEND("RestoreSend"),

  /** The element a failed backup data step reports its error under. */
  BACKUP_GET("BackupGet"),

  /** The element a failed backup integrity check reports its error under. */
  BACKUP_CHECK("BackupCheck"),

  /** The element a backup step reports an out-of-order or cancelled error under. */
  BACKUP("Backup");

  private final String elementName;

  ResponseElement(String elementName) {
    this.elementName = elementName;
  }

  /**
   * Recognises an element name.
   *
   * @param name the name of an element in a response
   * @return the matching element, or empty if this version does not know it
   */
  public static Optional<ResponseElement> of(String name) {
    return Arrays.stream(values()).filter(known -> known.elementName.equals(name)).findFirst();
  }

  /**
   * Returns the element name as the device writes it.
   *
   * @return the element name
   */
  public String elementName() {
    return elementName;
  }
}
