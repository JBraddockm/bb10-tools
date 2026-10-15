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
  PIN_MISMATCH;

  @Override
  public String wireName() {
    return name();
  }
}
