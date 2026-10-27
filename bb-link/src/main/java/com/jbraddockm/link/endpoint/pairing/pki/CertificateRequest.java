package com.jbraddockm.link.endpoint.pairing.pki;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.Signature;
import java.util.List;

/**
 * A PKCS #10 certificate request: a public key and a subject name, signed with the private key to
 * show that the sender has it.
 *
 * <p>The request has no attributes and no extensions. A device that signs it adds what it wants.
 */
public final class CertificateRequest {

  /** The longest common name that X.520 allows. */
  public static final int MAXIMUM_COMMON_NAME_LENGTH = 64;

  private static final String LABEL = "CERTIFICATE REQUEST";

  private final byte[] encoded;

  private CertificateRequest(byte[] encoded) {
    this.encoded = encoded;
  }

  /**
   * One entry of a subject name: an attribute and its value.
   *
   * @param objectIdentifier the attribute, as dotted digits
   * @param value the value, written as a UTF8String
   */
  public record Rdn(String objectIdentifier, String value) {

    /** The common name attribute. */
    public static final String COMMON_NAME = "2.5.4.3";

    /**
     * Checks the components.
     *
     * @throws IllegalArgumentException if the value is blank
     */
    public Rdn {
      if (value.isBlank()) {
        throw new IllegalArgumentException("an attribute of a name is not blank");
      }
    }

    /**
     * A common name.
     *
     * @param value the name
     * @return the entry
     */
    public static Rdn commonName(String value) {
      return new Rdn(COMMON_NAME, value);
    }
  }

  /**
   * Builds and signs a request whose subject has several entries, in the order given and each as
   * its own set.
   *
   * @param keys the key pair; the request holds the public key and is signed with the private one
   * @param subject the entries of the name; a common name is 64 characters at most
   * @param scheme how to sign
   * @return the request
   * @throws IllegalArgumentException if the subject is empty or a common name is too long
   * @throws GeneralSecurityException if the key cannot sign with the scheme
   */
  public static CertificateRequest withSubject(
      KeyPair keys, List<Rdn> subject, SignatureScheme scheme) throws GeneralSecurityException {
    if (subject.isEmpty()) {
      throw new IllegalArgumentException("a subject has an entry at least");
    }
    for (var entry : subject) {
      if (entry.objectIdentifier().equals(Rdn.COMMON_NAME)
          && entry.value().length() > MAXIMUM_COMMON_NAME_LENGTH) {
        throw new IllegalArgumentException(
            "the common name is 1 to " + MAXIMUM_COMMON_NAME_LENGTH + " characters");
      }
    }
    var name =
        Der.sequence(
            subject.stream()
                .map(
                    entry ->
                        Der.set(
                            Der.sequence(
                                Der.objectIdentifier(entry.objectIdentifier()),
                                Der.utf8String(entry.value()))))
                .toArray(byte[][]::new));
    // getEncoded() of a public key is the SubjectPublicKeyInfo that the request holds.
    var information =
        Der.sequence(Der.integer(0), name, keys.getPublic().getEncoded(), Der.contextual(0));
    var signer = Signature.getInstance(scheme.jcaName());
    signer.initSign(keys.getPrivate());
    signer.update(information);
    return new CertificateRequest(
        Der.sequence(information, scheme.algorithmIdentifier(), Der.bitString(signer.sign())));
  }

  /**
   * The request as DER.
   *
   * @return a copy of the bytes
   */
  public byte[] der() {
    return encoded.clone();
  }

  /**
   * The request as text, the form that a device is sent.
   *
   * @return the PEM block
   */
  public String pem() {
    return Pem.encode(LABEL, encoded);
  }
}
