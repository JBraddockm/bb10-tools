package com.jbraddockm.link.xml;

import java.io.Serial;

/** Signals that a device response is not well-formed XML. */
public final class MalformedXmlException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param message what is wrong
   * @param cause the parser's exception, or {@code null}
   */
  public MalformedXmlException(String message, Throwable cause) {
    super(message, cause);
  }
}
