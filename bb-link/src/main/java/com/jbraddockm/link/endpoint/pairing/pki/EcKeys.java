package com.jbraddockm.link.endpoint.pairing.pki;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;

/** Elliptic-curve key pairs. */
public final class EcKeys {

  private EcKeys() {}

  /**
   * Makes a key pair on the P-521 curve: the kind of the authority that BlackBerry Link makes
   * **[verified]**.
   *
   * @return the key pair
   * @throws GeneralSecurityException if the JDK cannot make one
   */
  public static KeyPair generateP521() throws GeneralSecurityException {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp521r1"));
    return generator.generateKeyPair();
  }
}
