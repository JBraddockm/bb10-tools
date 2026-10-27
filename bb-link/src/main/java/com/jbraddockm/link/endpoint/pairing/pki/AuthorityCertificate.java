package com.jbraddockm.link.endpoint.pairing.pki;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;

/**
 * A certificate authority that a key makes for itself: the form in which a computer is offered to a
 * device that keeps the authorities of the computers it is paired with.
 *
 * <p>It is made like the one that BlackBerry Link leaves in a phone **[verified]**: self-signed,
 * version 3, a subject of a common name only, and the same extensions in the same order.
 */
public final class AuthorityCertificate {

  private static final String COMMON_NAME = "2.5.4.3";
  private static final String KEY_USAGE = "2.5.29.15";
  private static final String BASIC_CONSTRAINTS = "2.5.29.19";
  private static final String SUBJECT_KEY_IDENTIFIER = "2.5.29.14";
  private static final String AUTHORITY_KEY_IDENTIFIER = "2.5.29.35";
  private static final String LABEL = "CERTIFICATE";

  private final byte[] encoded;

  private AuthorityCertificate(byte[] encoded) {
    this.encoded = encoded;
  }

  /**
   * Makes and signs a certificate authority for itself, as the one that BlackBerry Link left on a
   * phone **[verified]**: the subject is a common name only; the extensions are, in this order, a
   * critical basic constraint that it is an authority, a subject key identifier, an authority key
   * identifier with the same key identifier, and a critical key usage of digital signature,
   * non-repudiation, certificate signing and CRL signing.
   *
   * @param keys the key pair
   * @param scheme how to sign; it must suit the key
   * @param commonName the common name, 1 to 64 characters
   * @param serial the serial number, positive
   * @param notBefore when the validity starts
   * @param notAfter when the validity ends
   * @return the certificate
   * @throws IllegalArgumentException if the name is blank or too long, or the serial is not
   *     positive
   * @throws GeneralSecurityException if the key cannot sign with the scheme
   */
  public static AuthorityCertificate create(
      KeyPair keys,
      SignatureScheme scheme,
      String commonName,
      BigInteger serial,
      Instant notBefore,
      Instant notAfter)
      throws GeneralSecurityException {
    if (commonName.isBlank()
        || commonName.length() > CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "the common name is 1 to "
              + CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH
              + " characters");
    }
    if (serial.signum() <= 0) {
      throw new IllegalArgumentException("the serial number is positive");
    }
    var subject =
        Der.sequence(
            Der.set(Der.sequence(Der.objectIdentifier(COMMON_NAME), Der.utf8String(commonName))));
    var publicKeyInfo = keys.getPublic().getEncoded();
    var keyIdentifier = MessageDigest.getInstance("SHA-1").digest(publicKeyInfo);
    var authorityKeyIdentifier = new byte[keyIdentifier.length + 2];
    authorityKeyIdentifier[0] = (byte) 0x80;
    authorityKeyIdentifier[1] = (byte) keyIdentifier.length;
    System.arraycopy(keyIdentifier, 0, authorityKeyIdentifier, 2, keyIdentifier.length);
    var extensions =
        Der.contextual(
            3,
            Der.sequence(
                extension(
                    BASIC_CONSTRAINTS, true, Der.sequence(new byte[] {0x01, 0x01, (byte) 0xFF})),
                extension(SUBJECT_KEY_IDENTIFIER, false, Der.octetString(keyIdentifier)),
                extension(AUTHORITY_KEY_IDENTIFIER, false, Der.sequence(authorityKeyIdentifier)),
                extension(KEY_USAGE, true, new byte[] {0x03, 0x02, 0x01, (byte) 0xC6})));
    var toBeSigned =
        Der.sequence(
            Der.contextual(0, Der.integer(2)),
            Der.integer(serial),
            scheme.algorithmIdentifier(),
            subject,
            Der.sequence(Der.time(notBefore), Der.time(notAfter)),
            subject,
            publicKeyInfo,
            extensions);
    var signer = Signature.getInstance(scheme.jcaName());
    signer.initSign(keys.getPrivate());
    signer.update(toBeSigned);
    return new AuthorityCertificate(
        Der.sequence(toBeSigned, scheme.algorithmIdentifier(), Der.bitString(signer.sign())));
  }

  /**
   * The certificate as DER.
   *
   * @return a copy of the bytes
   */
  public byte[] der() {
    return encoded.clone();
  }

  /**
   * The certificate as text.
   *
   * @return the PEM block
   */
  public String pem() {
    return Pem.encode(LABEL, encoded);
  }

  private static byte[] extension(String oid, boolean critical, byte[] value) {
    var criticalFlag = critical ? new byte[] {0x01, 0x01, (byte) 0xFF} : new byte[0];
    return Der.sequence(Der.objectIdentifier(oid), criticalFlag, Der.octetString(value));
  }
}
