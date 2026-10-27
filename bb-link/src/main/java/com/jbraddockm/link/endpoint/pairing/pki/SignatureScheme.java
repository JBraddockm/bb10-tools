package com.jbraddockm.link.endpoint.pairing.pki;

/** The ways to sign that a certificate or a request may name. */
public enum SignatureScheme {

  /** SHA-256 with RSA. */
  SHA256_WITH_RSA("SHA256withRSA", "1.2.840.113549.1.1.11"),

  /**
   * SHA-1 with RSA: what a device's authority signs its certificates with, and what a request to it
   * is signed with.
   */
  SHA1_WITH_RSA("SHA1withRSA", "1.2.840.113549.1.1.5"),

  /**
   * SHA-1 with ECDSA: what the authority that BlackBerry Link makes is signed with **[verified]**.
   * It is weak, and offered only to make the same certificate.
   */
  SHA1_WITH_ECDSA("SHA1withECDSA", "1.2.840.10045.4.1");

  private final String jcaName;
  private final String objectIdentifier;

  SignatureScheme(String jcaName, String objectIdentifier) {
    this.jcaName = jcaName;
    this.objectIdentifier = objectIdentifier;
  }

  /**
   * The name that the JDK knows the scheme by.
   *
   * @return the name
   */
  public String jcaName() {
    return jcaName;
  }

  /**
   * The AlgorithmIdentifier that names the scheme in a request or a certificate. The RSA schemes
   * carry a NULL as their parameters; ECDSA has none.
   *
   * @return the encoded element
   */
  public byte[] algorithmIdentifier() {
    return this == SHA1_WITH_ECDSA
        ? Der.sequence(Der.objectIdentifier(objectIdentifier))
        : Der.sequence(Der.objectIdentifier(objectIdentifier), Der.nullValue());
  }
}
