package com.jbraddockm.link;

import com.jbraddockm.core.error.ErrorCode;

/** The error codes that only bb-link uses, in addition to the shared ones. */
public enum LinkErrorCode implements ErrorCode {

  /**
   * The device answered with something this version does not know how to read. The element or
   * content type is named in the error details.
   */
  UNEXPECTED_RESPONSE,

  /**
   * The confirmation PIN of a command that changes who the device trusts does not match the PIN the
   * device reports. Nothing was sent to change the device.
   */
  PIN_MISMATCH,

  /**
   * The device asked for a password during the login and none was given. No answer was sent, so no
   * failed attempt was counted. A client can prompt for the password and connect again.
   */
  PASSWORD_REQUIRED,

  /**
   * The device does not have enough free space for what was to be installed. Nothing was sent to
   * install. The details give the bytes needed and the bytes free.
   */
  NOT_ENOUGH_SPACE,

  /** The folder of the device already has a pairing, and it was not asked to replace it. */
  PAIRING_EXISTS,

  /** The folder of the device already has a certificate, and it was not asked to replace it. */
  IDENTITY_EXISTS,

  /** The device returned a certificate that cannot be used: it is not X.509, or not for our key. */
  CERTIFICATE_REJECTED;

  @Override
  public String wireName() {
    return name();
  }
}
