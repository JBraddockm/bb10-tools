package com.jbraddockm.link.endpoint.pairing.pki;

import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.time.Instant;

/**
 * A leaf certificate that an authority signs for another key: a non-self-signed, end-entity X.509 v3
 * certificate whose issuer is {@link AuthorityCertificate the authority} and whose subject is a new
 * key. This is the one primitive the authority-as-hub model is missing — {@link AuthorityCertificate}
 * only self-signs — so with it the pairing authority can mint the per-service identities (control,
 * Blend, tunnel) in process instead of by terminal {@code openssl} (see
 * {@code analysis/CERT-STREAMLINING-PRD.md} §P0).
 *
 * <p>Shape: version 3; a subject of a common name only; no basic constraint (an end entity, not an
 * authority); a subject key identifier of the leaf key; an authority key identifier holding the
 * <b>issuer's</b> key identifier; and a critical key usage of digital signature and key encipherment
 * (a TLS client leaf). The in-process form of {@code openssl x509 -req -CA … -CAkey …}.
 */
final class IssuedCertificate {

  private static final String COMMON_NAME = "2.5.4.3";
  private static final String KEY_USAGE = "2.5.29.15";
  private static final String SUBJECT_KEY_IDENTIFIER = "2.5.29.14";
  private static final String AUTHORITY_KEY_IDENTIFIER = "2.5.29.35";
  private static final String LABEL = "CERTIFICATE";

  private final byte[] encoded;

  private IssuedCertificate(byte[] encoded) {
    this.encoded = encoded;
  }

  /**
   * Signs a leaf for {@code subjectPublicKey} under the issuer.
   *
   * @param subjectPublicKey the key the leaf certifies
   * @param subjectCommonName the leaf's common name, 1 to 64 characters
   * @param issuerPublicKey the issuer's key — its SHA-1 becomes the authority key identifier, linking
   *     the leaf to the enrolled authority
   * @param issuerCommonName the issuer's common name (the authority's subject), 1 to 64 characters
   * @param issuerKey the issuer's private key, which signs the leaf
   * @param scheme how to sign; it must suit {@code issuerKey}
   * @param serial the serial number, positive
   * @param notBefore when the validity starts
   * @param notAfter when the validity ends
   * @return the leaf certificate
   * @throws IllegalArgumentException if a name is blank or too long, or the serial is not positive
   * @throws GeneralSecurityException if the issuer key cannot sign with the scheme
   */
  public static IssuedCertificate create(
      PublicKey subjectPublicKey,
      String subjectCommonName,
      PublicKey issuerPublicKey,
      String issuerCommonName,
      PrivateKey issuerKey,
      SignatureScheme scheme,
      BigInteger serial,
      Instant notBefore,
      Instant notAfter)
      throws GeneralSecurityException {
    requireCommonName(subjectCommonName, "subject");
    requireCommonName(issuerCommonName, "issuer");
    if (serial.signum() <= 0) {
      throw new IllegalArgumentException("the serial number is positive");
    }
    var subjectPublicKeyInfo = subjectPublicKey.getEncoded();
    var sha1 = MessageDigest.getInstance("SHA-1");
    var subjectKeyIdentifier = sha1.digest(subjectPublicKeyInfo);
    var issuerKeyIdentifier = sha1.digest(issuerPublicKey.getEncoded());

    // AuthorityKeyIdentifier ::= SEQUENCE { keyIdentifier [0] OCTET STRING } — the issuer's SKI.
    var authorityKeyIdentifier = new byte[issuerKeyIdentifier.length + 2];
    authorityKeyIdentifier[0] = (byte) 0x80;
    authorityKeyIdentifier[1] = (byte) issuerKeyIdentifier.length;
    System.arraycopy(issuerKeyIdentifier, 0, authorityKeyIdentifier, 2, issuerKeyIdentifier.length);

    var extensions =
        Der.contextual(
            3,
            Der.sequence(
                extension(SUBJECT_KEY_IDENTIFIER, false, Der.octetString(subjectKeyIdentifier)),
                extension(AUTHORITY_KEY_IDENTIFIER, false, Der.sequence(authorityKeyIdentifier)),
                // KeyUsage: digitalSignature + keyEncipherment (bits 0 and 2), critical.
                extension(KEY_USAGE, true, new byte[] {0x03, 0x02, 0x05, (byte) 0xA0})));
    var toBeSigned =
        Der.sequence(
            Der.contextual(0, Der.integer(2)),
            Der.integer(serial),
            scheme.algorithmIdentifier(),
            name(issuerCommonName),
            Der.sequence(Der.time(notBefore), Der.time(notAfter)),
            name(subjectCommonName),
            subjectPublicKeyInfo,
            extensions);
    var signer = Signature.getInstance(scheme.jcaName());
    signer.initSign(issuerKey);
    signer.update(toBeSigned);
    return new IssuedCertificate(
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

  /** A Name of a single common name: {@code SEQUENCE { SET { SEQUENCE { OID cn, UTF8String } } } }. */
  private static byte[] name(String commonName) {
    return Der.sequence(
        Der.set(Der.sequence(Der.objectIdentifier(COMMON_NAME), Der.utf8String(commonName))));
  }

  private static void requireCommonName(String commonName, String which) {
    if (commonName.isBlank()
        || commonName.length() > CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH) {
      throw new IllegalArgumentException(
          "the "
              + which
              + " common name is 1 to "
              + CertificateRequest.MAXIMUM_COMMON_NAME_LENGTH
              + " characters");
    }
  }

  private static byte[] extension(String oid, boolean critical, byte[] value) {
    var criticalFlag = critical ? new byte[] {0x01, 0x01, (byte) 0xFF} : new byte[0];
    return Der.sequence(Der.objectIdentifier(oid), criticalFlag, Der.octetString(value));
  }
}
