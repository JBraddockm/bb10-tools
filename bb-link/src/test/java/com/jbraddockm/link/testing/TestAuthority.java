package com.jbraddockm.link.testing;

import com.jbraddockm.link.pairing.pki.CertificateRequest;
import com.jbraddockm.link.pairing.pki.Der;
import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.pki.RsaKeys;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A certificate authority that signs requests the way a device's does, as far as a certificate that
 * a device issued shows it: the serial number goes into the subject as well, the validity is 37
 * days, the signature is SHA-1 with RSA, and the extensions are the ones that the certificate from
 * a laptop has.
 *
 * <p>It exists so that the issuing of a certificate can be tested without a device and without
 * {@code openssl}.
 */
public final class TestAuthority {

  private static final String COMMON_NAME = "2.5.4.3";
  private static final String SERIAL_NUMBER = "2.5.4.5";

  private final KeyPair keys;
  private final AtomicLong nextSerial = new AtomicLong(29_717);
  private final Instant created = Instant.now().truncatedTo(ChronoUnit.SECONDS);

  /**
   * Makes an authority with a new key.
   *
   * @throws GeneralSecurityException if the JDK cannot make the key
   */
  public TestAuthority() throws GeneralSecurityException {
    this.keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
  }

  /**
   * What the authority made for a request.
   *
   * @param pem the certificate
   * @param commonName the common name that the request had
   */
  public record Issued(String pem, String commonName) {}

  /**
   * Signs a request, if it is a valid one: its signature must verify with the key that it holds,
   * which shows that the sender has the private key.
   *
   * @param requestPem the request as PEM
   * @return the certificate, or empty if the request is not valid
   */
  public Optional<Issued> sign(String requestPem) {
    try {
      var block = Pem.first(requestPem, "CERTIFICATE REQUEST");
      if (block.isEmpty()) {
        return Optional.empty();
      }
      var request = TestDer.read(block.get().content(), 0).children();
      var information = request.get(0);
      var fields = information.children();
      var subject = fields.get(1);
      var publicKeyInfo = fields.get(2);
      var scheme = schemeOf(request.get(1).children().getFirst().content());
      var signature = request.get(2).content();
      var verifier = Signature.getInstance(scheme.jcaName());
      verifier.initVerify(
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(publicKeyInfo.encoded())));
      verifier.update(information.encoded());
      if (!verifier.verify(signature, 1, signature.length - 1)) {
        return Optional.empty();
      }
      var commonName = commonNameOf(subject);
      var serial = nextSerial.getAndIncrement();
      var now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
      var subjectName =
          Der.sequence(
              Der.set(Der.sequence(Der.objectIdentifier(COMMON_NAME), Der.utf8String(commonName))),
              Der.set(
                  Der.sequence(
                      Der.objectIdentifier(SERIAL_NUMBER), Der.utf8String(Long.toString(serial)))));
      var certificate =
          sign(
              subjectName,
              publicKeyInfo.encoded(),
              issuerName(),
              BigInteger.valueOf(serial),
              now,
              now.plus(Duration.ofDays(37)),
              false);
      return Optional.of(new Issued(Pem.encode("CERTIFICATE", certificate), commonName));
    } catch (GeneralSecurityException | RuntimeException _) {
      return Optional.empty();
    }
  }

  /**
   * The certificate of the authority itself, which a device hands out as its root.
   *
   * @return the certificate as PEM
   * @throws GeneralSecurityException if it cannot be signed
   */
  public String rootPem() throws GeneralSecurityException {
    var certificate =
        sign(
            issuerName(),
            keys.getPublic().getEncoded(),
            issuerName(),
            BigInteger.ONE,
            created,
            created.plus(Duration.ofDays(3650)),
            true);
    return Pem.encode("CERTIFICATE", certificate);
  }

  private static byte[] issuerName() {
    return Der.sequence(
        rdn("2.5.4.10", "Research In Motion"),
        rdn("2.5.4.11", "BlackBerry"),
        rdn(COMMON_NAME, "BB10/f09014cf1810c3d9fa757ca0e7aa9c42117dab02"));
  }

  private static byte[] rdn(String oid, String value) {
    return Der.set(Der.sequence(Der.objectIdentifier(oid), Der.utf8String(value)));
  }

  private byte[] sign(
      byte[] subject,
      byte[] publicKeyInfo,
      byte[] issuer,
      BigInteger serial,
      Instant notBefore,
      Instant notAfter,
      boolean authority)
      throws GeneralSecurityException {
    var scheme = SignatureScheme.SHA1_WITH_RSA;
    var keyIdentifier = MessageDigest.getInstance("SHA-1").digest(publicKeyInfo);
    var extensions =
        Der.contextual(
            3,
            Der.sequence(
                extension("2.5.29.15", new byte[] {0x03, 0x02, 0x05, (byte) 0xE0}),
                extension("2.5.29.19", authority ? Der.sequence(Der.bool(true)) : Der.sequence()),
                extension("2.5.29.14", Der.octetString(keyIdentifier)),
                extension(
                    "2.16.840.1.113730.1.13",
                    concatenate(
                        new byte[] {0x16, 29},
                        "OpenSSL Generated Certificate".getBytes(StandardCharsets.US_ASCII)))));
    var toBeSigned =
        Der.sequence(
            Der.contextual(0, Der.integer(2)),
            Der.integer(serial),
            scheme.algorithmIdentifier(),
            issuer,
            Der.sequence(Der.time(notBefore), Der.time(notAfter)),
            subject,
            publicKeyInfo,
            extensions);
    var signer = Signature.getInstance(scheme.jcaName());
    signer.initSign(keys.getPrivate());
    signer.update(toBeSigned);
    return Der.sequence(toBeSigned, scheme.algorithmIdentifier(), Der.bitString(signer.sign()));
  }

  private static byte[] extension(String oid, byte[] value) {
    return Der.sequence(Der.objectIdentifier(oid), Der.octetString(value));
  }

  private static byte[] concatenate(byte[] first, byte[] second) {
    return Der.concatenate(first, second);
  }

  private static SignatureScheme schemeOf(byte[] objectIdentifierContent) {
    // 1.2.840.113549.1.1.11 ends in 0x0B, and 1.2.840.113549.1.1.5 in 0x05
    return objectIdentifierContent[objectIdentifierContent.length - 1] == 0x0B
        ? SignatureScheme.SHA256_WITH_RSA
        : SignatureScheme.SHA1_WITH_RSA;
  }

  private static String commonNameOf(TestDer.Element subject) {
    for (var set : subject.children()) {
      var pair = set.children().getFirst().children();
      // 2.5.4.3 is 55 04 03
      if (pair.get(0).content().length == 3 && pair.get(0).content()[2] == 0x03) {
        return new String(pair.get(1).content(), StandardCharsets.UTF_8);
      }
    }
    throw new IllegalArgumentException("the request has no common name");
  }

  /**
   * A request that a device must refuse, because its signature is not the key's own.
   *
   * @param commonName the name to put in it
   * @return the request as PEM
   * @throws GeneralSecurityException if it cannot be made
   */
  public static String requestWithWrongSignature(String commonName)
      throws GeneralSecurityException {
    var signer = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var real =
        CertificateRequest.withSubject(
            signer,
            List.of(CertificateRequest.Rdn.commonName(commonName)),
            SignatureScheme.SHA256_WITH_RSA);
    var der = real.der();
    der[der.length - 1] ^= 0x01;
    return Pem.encode("CERTIFICATE REQUEST", der);
  }
}
