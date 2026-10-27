package com.jbraddockm.link.update;

import com.jbraddockm.link.cgi.WireValue;

/** The kind of package in an upload; applications and the operating system are both {@code bar}. */
enum UploadKind implements WireValue {

  /** An application, or an operating system image. */
  BAR("bar"),

  /** A radio image. */
  RADIO("radio");

  private final String wireName;

  UploadKind(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
