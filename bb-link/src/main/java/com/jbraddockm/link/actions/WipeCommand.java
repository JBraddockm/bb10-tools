package com.jbraddockm.link.actions;

import com.jbraddockm.link.cgi.WireValue;

/** The values of the {@code wipe} form field that bb-link sends. */
enum WipeCommand implements WireValue {

  /** Reads the device PIN. Changes nothing. */
  GET_PIN("getpin");

  private final String wireName;

  WipeCommand(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
