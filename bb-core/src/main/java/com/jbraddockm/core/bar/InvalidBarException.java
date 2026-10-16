package com.jbraddockm.core.bar;

import java.io.Serial;

/**
 * A file that was read but is not a usable bar: not a ZIP archive, without a manifest, or with a
 * manifest that cannot be understood. The message says what is wrong, without naming the file; the
 * caller knows which one it asked about.
 */
public final class InvalidBarException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates the exception.
   *
   * @param reason what is wrong with the file
   */
  public InvalidBarException(String reason) {
    super(reason);
  }
}
