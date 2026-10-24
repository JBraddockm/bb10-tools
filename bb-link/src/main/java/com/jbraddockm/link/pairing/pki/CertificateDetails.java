package com.jbraddockm.link.pairing.pki;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import javax.naming.InvalidNameException;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;

/**
 * What can be read from a certificate without trusting it.
 *
 * @param subject the subject, as {@code CN=…, SERIALNUMBER=…}: the parts in the certificate's own
 *     order, most general first, without escapes
 * @param issuer the issuer, in the same form
 * @param serialNumber the serial number
 * @param notBefore when it becomes valid
 * @param notAfter when it stops being valid
 * @param sha256Fingerprint the SHA-256 of its encoding, as uppercase hex pairs with colons
 * @param publicKeyAlgorithm the algorithm of its key, such as {@code RSA}
 * @param publicKeyBits the size of its key; 0 for a key of a kind that is not measured
 * @param signatureAlgorithm the algorithm that signed it
 * @param certificateAuthority whether it may sign other certificates
 */
public record CertificateDetails(
    String subject,
    String issuer,
    BigInteger serialNumber,
    Instant notBefore,
    Instant notAfter,
    String sha256Fingerprint,
    String publicKeyAlgorithm,
    int publicKeyBits,
    String signatureAlgorithm,
    boolean certificateAuthority) {

  /** The attributes that the JDK does not name itself, and that a certificate of a device has. */
  private static final Map<String, String> KEYWORDS =
      Map.of("2.5.4.5", "SERIALNUMBER", "2.5.4.13", "DESCRIPTION");

  /**
   * Reads a certificate.
   *
   * @param certificate the certificate
   * @return what it says
   */
  public static CertificateDetails of(X509Certificate certificate) {
    var key = certificate.getPublicKey();
    var bits =
        switch (key) {
          case RSAPublicKey rsa -> rsa.getModulus().bitLength();
          case ECPublicKey ec -> ec.getParams().getOrder().bitLength();
          default -> 0;
        };
    return new CertificateDetails(
        describe(certificate.getSubjectX500Principal()),
        describe(certificate.getIssuerX500Principal()),
        certificate.getSerialNumber(),
        certificate.getNotBefore().toInstant(),
        certificate.getNotAfter().toInstant(),
        fingerprint(encoded(certificate)),
        key.getAlgorithm(),
        bits,
        certificate.getSigAlgName(),
        certificate.getBasicConstraints() >= 0);
  }

  /**
   * Reads a certificate from its encoding.
   *
   * @param der the bytes
   * @return the certificate
   * @throws CertificateException if the bytes are not an X.509 certificate
   */
  public static X509Certificate certificate(byte[] der) throws CertificateException {
    var factory = CertificateFactory.getInstance("X.509");
    return (X509Certificate) factory.generateCertificate(new ByteArrayInputStream(der));
  }

  /**
   * Reads the first certificate of a text.
   *
   * @param pem a text with a {@code CERTIFICATE} block
   * @return the certificate
   * @throws CertificateException if there is no such block, or it is not X.509
   */
  public static X509Certificate firstCertificate(String pem) throws CertificateException {
    try {
      var block =
          Pem.first(pem, "CERTIFICATE")
              .orElseThrow(() -> new CertificateException("the text has no CERTIFICATE block"));
      return certificate(block.content());
    } catch (IllegalArgumentException notBase64) {
      throw new CertificateException("the CERTIFICATE block is not base64", notBase64);
    }
  }

  /**
   * Whole days from a moment to the end of the validity; negative once it has ended.
   *
   * @param now the moment
   * @return the days, rounded towards zero
   */
  public long daysLeft(Instant now) {
    return ChronoUnit.DAYS.between(now, notAfter);
  }

  /**
   * Whether the validity has ended.
   *
   * @param now the moment
   * @return {@code true} if it is after the end
   */
  public boolean expiredAt(Instant now) {
    return now.isAfter(notAfter);
  }

  /**
   * Writes a name as a certificate viewer does: the parts in the order that the certificate has
   * them, the most general first, and without the escapes that a single-line format adds.
   */
  private static String describe(X500Principal principal) {
    try {
      return new LdapName(principal.getName(X500Principal.RFC2253, KEYWORDS))
          .getRdns().stream()
              .map(part -> part.getType() + "=" + valueOf(part.getValue()))
              .collect(Collectors.joining(", "));
    } catch (InvalidNameException _) {
      return principal.getName();
    }
  }

  /** An attribute that the JDK cannot decode is a byte array: written as {@code #} and hex. */
  private static String valueOf(Object value) {
    return value instanceof byte[] bytes ? "#" + HexFormat.of().formatHex(bytes) : value.toString();
  }

  private static byte[] encoded(X509Certificate certificate) {
    try {
      return certificate.getEncoded();
    } catch (CertificateException failure) {
      // Cannot happen: the certificate was returned by the JDK and always has an encoding.
      throw new IllegalStateException("a parsed certificate has an encoding", failure);
    }
  }

  private static String fingerprint(byte[] der) {
    try {
      var digest = MessageDigest.getInstance("SHA-256").digest(der);
      var hex = HexFormat.of().withUpperCase().formatHex(digest);
      return IntStream.range(0, hex.length() / 2)
          .mapToObj(index -> hex.substring(index * 2, index * 2 + 2))
          .collect(Collectors.joining(":"));
    } catch (NoSuchAlgorithmException failure) {
      // Cannot happen: SHA-256 has been a required algorithm since Java 7.
      throw new IllegalStateException("every JDK has SHA-256", failure);
    }
  }
}
