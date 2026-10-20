package com.jbraddockm.link.testing;

import java.security.SecureRandom;
import java.util.Arrays;

/** A random source that always yields the same byte, so that generated values are predictable. */
public final class FixedRandom extends SecureRandom {

  private static final long serialVersionUID = 1L;

  private final byte value;

  /**
   * Makes the source.
   *
   * @param value the byte that every random byte is
   */
  public FixedRandom(int value) {
    this.value = (byte) value;
  }

  @Override
  public void nextBytes(byte[] bytes) {
    Arrays.fill(bytes, value);
  }
}
