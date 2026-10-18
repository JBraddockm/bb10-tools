package com.jbraddockm.link.pairing.pki;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/** Makes and writes RSA keys. */
public final class RsaKeys {

  /**
   * The size that a device's own certificates have, and the largest whose request still fits the
   * 1024 bytes that a device takes.
   */
  public static final int DEVICE_KEY_BITS = 2048;

  private static final String PRIVATE_KEY_LABEL = "PRIVATE KEY";

  private RsaKeys() {}

  /**
   * Makes a key pair.
   *
   * @param bits the modulus size
   * @return the key pair
   * @throws GeneralSecurityException if the JDK cannot make RSA keys
   */
  public static KeyPair generate(int bits) throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(bits);
    return generator.generateKeyPair();
  }
}
