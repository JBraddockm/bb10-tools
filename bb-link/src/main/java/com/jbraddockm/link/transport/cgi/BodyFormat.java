package com.jbraddockm.link.transport.cgi;

/** What a device answer was turned into. */
public enum BodyFormat {

  /** The answer is JSON; the value is the parsed tree. */
  JSON("json"),

  /** The answer is XML; the value is its content as JSON (see {@code XmlJson}). */
  XML("xml"),

  /** The answer is neither, or not well-formed; the value is the text as it was sent. */
  TEXT("text");

  private final String wireName;

  BodyFormat(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the name used in the wire protocol.
   *
   * @return {@code json}, {@code xml} or {@code text}
   */
  public String wireName() {
    return wireName;
  }
}
