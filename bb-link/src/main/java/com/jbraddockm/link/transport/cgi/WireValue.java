package com.jbraddockm.link.transport.cgi;

/** A value that appears in a request exactly as the device expects it. */
public interface WireValue {

  /**
   * Returns the text sent to the device.
   *
   * @return the wire text
   */
  String wireName();
}
