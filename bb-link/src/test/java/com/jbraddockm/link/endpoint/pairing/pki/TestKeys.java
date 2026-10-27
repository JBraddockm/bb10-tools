package com.jbraddockm.link.endpoint.pairing.pki;

import com.jbraddockm.link.testing.Resources;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;

/**
 * Key pairs for tests that need a key but do not test key generation. They are loaded from test
 * resources.
 */
public final class TestKeys {

  private TestKeys() {}

  /**
   * An RSA key of 2048 bits.
   *
   * @return the key pair
   */
  public static KeyPair rsa() {
    return load("RSA", "test-key--rsa-2048");
  }

  /**
   * Another RSA key of 2048 bits, for the tests of what a key does not match.
   *
   * @return the key pair
   */
  public static KeyPair otherRsa() {
    return load("RSA", "test-key--rsa-2048-other");
  }

  /**
   * An elliptic-curve key on P-521.
   *
   * @return the key pair
   */
  public static KeyPair ec() {
    return load("EC", "test-key--ec-p521");
  }

  private static KeyPair load(String algorithm, String name) {
    try {
      var factory = KeyFactory.getInstance(algorithm);
      var privateKey =
          Pem.first(Resources.text(TestKeys.class, name + ".pem"), "PRIVATE KEY")
              .orElseThrow()
              .content();
      var publicKey =
          Pem.first(Resources.text(TestKeys.class, name + "--public.pem"), "PUBLIC KEY")
              .orElseThrow()
              .content();
      return new KeyPair(
          factory.generatePublic(new X509EncodedKeySpec(publicKey)),
          factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey)));
    } catch (GeneralSecurityException failure) {
      throw new IllegalStateException("the test key " + name + " cannot be read", failure);
    }
  }
}
