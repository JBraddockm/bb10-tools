package com.jbraddockm.link.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestDer;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPublicKey;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link AuthorityCertificate}: the self-made certificate authority that is offered to
 * the device.
 */
@DisplayName("AuthorityCertificate: a certificate authority that a key makes for itself")
class AuthorityCertificateTest {

  private static final String COMMON_NAME = "PC issuer 0000000000000000";
  private static final BigInteger SERIAL = BigInteger.valueOf(0x1234);
  private static final Instant NOT_BEFORE = Instant.parse("2026-10-11T10:00:00Z");
  private static final Instant NOT_AFTER = Instant.parse("2036-10-09T10:00:00Z");

  private final KeyPair keys = TestKeys.ec();
  private AuthorityCertificate authority;
  private X509Certificate certificate;

  @BeforeEach
  void makeTheAuthority() throws Exception {
    authority =
        AuthorityCertificate.create(
            keys, SignatureScheme.SHA1_WITH_ECDSA, COMMON_NAME, SERIAL, NOT_BEFORE, NOT_AFTER);
    certificate = CertificateDetails.certificate(authority.der());
  }

  @Nested
  @DisplayName("read as a certificate")
  class AsX509 {

    @Test
    @DisplayName("is a version 3 certificate with the serial number and the validity it was given")
    void hasWhatItWasGiven() {
      assertThat(certificate.getVersion()).isEqualTo(3);
      assertThat(certificate.getSerialNumber()).isEqualTo(SERIAL);
      assertThat(certificate.getNotBefore().toInstant()).isEqualTo(NOT_BEFORE);
      assertThat(certificate.getNotAfter().toInstant()).isEqualTo(NOT_AFTER);
    }

    @Test
    @DisplayName("is its own issuer, with a subject of one common name")
    void isItsOwnIssuer() {
      assertThat(certificate.getSubjectX500Principal().getName()).isEqualTo("CN=" + COMMON_NAME);
      assertThat(certificate.getIssuerX500Principal())
          .isEqualTo(certificate.getSubjectX500Principal());
    }

    @Test
    @DisplayName(
        "holds the public key of the pair, and is signed by the private key: the signature"
            + " verifies")
    void isSignedByItsKey() {
      assertThat(certificate.getPublicKey()).isEqualTo(keys.getPublic());
      assertThatCode(() -> certificate.verify(keys.getPublic())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("is signed with the scheme it was given")
    void isSignedWithTheScheme() {
      assertThat(certificate.getSigAlgName()).isEqualTo("SHA1withECDSA");
    }

    @Test
    @DisplayName("is a certificate authority, that may sign certificates and lists")
    void isACertificateAuthority() {
      assertThat(certificate.getBasicConstraints()).isGreaterThanOrEqualTo(0);
      assertThat(certificate.getKeyUsage())
          .containsExactly(true, true, false, false, false, true, true, false, false);
    }

    @Test
    @DisplayName(
        "names its key by the SHA-1 of the encoded public key, as subject and as authority key"
            + " identifier")
    void identifiesItsKey() throws Exception {
      var identifier = MessageDigest.getInstance("SHA-1").digest(keys.getPublic().getEncoded());

      assertThat(certificate.getExtensionValue("2.5.29.14"))
          .isEqualTo(Der.octetString(Der.octetString(identifier)));
      assertThat(certificate.getExtensionValue("2.5.29.35"))
          .isEqualTo(
              Der.octetString(
                  Der.sequence(Der.concatenate(new byte[] {(byte) 0x80, 0x14}, identifier))));
    }

    @Test
    @DisplayName(
        "is written with a UTCTime up to 2049, and with a GeneralizedTime after it, and is still"
            + " read")
    void readsDatesAfter2049() throws Exception {
      var late =
          AuthorityCertificate.create(
              keys,
              SignatureScheme.SHA1_WITH_ECDSA,
              COMMON_NAME,
              SERIAL,
              NOT_BEFORE,
              Instant.parse("2060-01-01T00:00:00Z"));

      assertThat(CertificateDetails.certificate(late.der()).getNotAfter().toInstant())
          .isEqualTo(Instant.parse("2060-01-01T00:00:00Z"));
    }

    @Test
    @DisplayName("can be made with an RSA key and an RSA scheme too")
    void worksWithRsa() throws Exception {
      var rsa = TestKeys.rsa();
      var made =
          AuthorityCertificate.create(
              rsa, SignatureScheme.SHA256_WITH_RSA, COMMON_NAME, SERIAL, NOT_BEFORE, NOT_AFTER);

      var read = CertificateDetails.certificate(made.der());

      assertThat(read.getSigAlgName()).isEqualTo("SHA256withRSA");
      assertThatCode(() -> read.verify(rsa.getPublic())).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("taken apart")
  class Parts {

    private List<TestDer.Element> toBeSigned() {
      return TestDer.read(authority.der(), 0).children().getFirst().children();
    }

    @Test
    @DisplayName("has the sections of a certificate in the order of the standard")
    void hasTheSectionsInOrder() {
      var parts = toBeSigned();

      assertThat(parts)
          .extracting(TestDer.Element::tag)
          .containsExactly(0xA0, 0x02, 0x30, 0x30, 0x30, 0x30, 0x30, 0xA3);
    }

    @Test
    @DisplayName(
        "names the scheme twice, in the information and after it, and the two are the same")
    void namesTheSchemeTwice() {
      var top = TestDer.read(authority.der(), 0).children();

      assertThat(toBeSigned().get(2).encoded())
          .isEqualTo(top.get(1).encoded())
          .isEqualTo(SignatureScheme.SHA1_WITH_ECDSA.algorithmIdentifier());
    }

    @Test
    @DisplayName("writes the subject twice, as issuer and as subject, byte for byte the same")
    void writesTheSubjectTwice() {
      assertThat(toBeSigned().get(3).encoded()).isEqualTo(toBeSigned().get(5).encoded());
    }

    @Test
    @DisplayName("holds the public key as it is encoded, and the validity as the two times")
    void holdsTheKeyAndTheTimes() {
      assertThat(toBeSigned().get(6).encoded()).isEqualTo(keys.getPublic().getEncoded());
      assertThat(toBeSigned().get(4).children())
          .extracting(TestDer.Element::encoded)
          .containsExactly(Der.time(NOT_BEFORE), Der.time(NOT_AFTER));
    }
  }

  @Nested
  @DisplayName("compared with the structure of an authority of BlackBerry")
  class AgainstBlackBerry {

    private Map<?, ?> structure;

    @BeforeEach
    void readTheStructure() {
      structure =
          (Map<?, ?>)
              Json.parse(
                  Resources.text(
                      AuthorityCertificateTest.class, "authority-structure--blackberry.json"));
    }

    @Test
    @DisplayName("has the same extensions, in the same order, with the same flags")
    void hasTheSameExtensions() {
      var extensions = toBeSignedExtensions();

      var expected =
          ((List<?>) structure.get("extensions"))
              .stream()
                  .map(entry -> (Map<?, ?>) entry)
                  .map(
                      entry ->
                          entry.get("oid")
                              + (Boolean.TRUE.equals(entry.get("critical")) ? " critical" : ""))
                  .toList();
      assertThat(extensions).isEqualTo(expected);
    }

    @Test
    @DisplayName("has the same version, scheme, curve, basic constraints and key usages")
    void hasTheSameBasics() {
      assertThat(certificate.getVersion()).isEqualTo(((Long) structure.get("version")).intValue());
      assertThat(certificate.getSigAlgOID()).isEqualTo(structure.get("signatureAlgorithmOid"));
      assertThat(((ECPublicKey) certificate.getPublicKey()).getParams().toString())
          .contains((String) structure.get("curve"));
      assertThat(certificate.getBasicConstraints() >= 0)
          .isEqualTo(structure.get("basicConstraintsCa"));
      var names =
          List.of(
              "digitalSignature",
              "nonRepudiation",
              "keyEncipherment",
              "dataEncipherment",
              "keyAgreement",
              "keyCertSign",
              "cRLSign");
      var usages = certificate.getKeyUsage();
      var used =
          IntStream.range(0, names.size())
              .filter(index -> usages[index])
              .mapToObj(names::get)
              .toList();
      assertThat(used).isEqualTo(structure.get("keyUsage"));
    }

    private List<String> toBeSignedExtensions() {
      var extensions =
          TestDer.read(authority.der(), 0)
              .children()
              .getFirst()
              .children()
              .get(7)
              .children()
              .getFirst();
      return extensions.children().stream()
          .map(
              extension -> {
                var parts = extension.children();
                var critical = parts.size() == 3 && parts.get(1).content()[0] != 0;
                return oidOf(parts.getFirst()) + (critical ? " critical" : "");
              })
          .toList();
    }

    private String oidOf(TestDer.Element identifier) {
      for (var candidate : List.of("2.5.29.19", "2.5.29.14", "2.5.29.35", "2.5.29.15")) {
        if (Arrays.equals(identifier.encoded(), Der.objectIdentifier(candidate))) {
          return candidate;
        }
      }
      return "unknown";
    }
  }

  @Test
  @DisplayName(
      "is about as large as the authority of a real device, 822 bytes, and well within the 1024"
          + " that a device takes")
  void isAboutTheSizeOfARealAuthority() {
    assertThat(Pem.size(authority.pem())).isBetween(780, 860).isLessThanOrEqualTo(1024);
  }

  @Nested
  @DisplayName("how it is written")
  class Writing {

    @Test
    @DisplayName("pem is a CERTIFICATE block of the bytes")
    void pemIsACertificateBlock() {
      assertThat(authority.pem()).startsWith("-----BEGIN CERTIFICATE-----\n");
      assertThat(Pem.first(authority.pem(), "CERTIFICATE"))
          .hasValueSatisfying(block -> assertThat(block.content()).isEqualTo(authority.der()));
    }

    @Test
    @DisplayName("der gives a copy: changing it does not change the certificate")
    void derGivesACopy() {
      var first = authority.der();
      first[0] = 0;

      assertThat(authority.der()[0]).isEqualTo((byte) 0x30);
    }
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusals {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank common name")
    void aBlankName(String blank) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  AuthorityCertificate.create(
                      keys, SignatureScheme.SHA1_WITH_ECDSA, blank, SERIAL, NOT_BEFORE, NOT_AFTER))
          .withMessageContaining("1 to 64 characters");
    }

    @Test
    @DisplayName("a common name of 65 characters, though 64 is allowed")
    void aNameOf65() throws Exception {
      var longest =
          Resources.text(AuthorityCertificateTest.class, "common-name--64-characters.txt").strip();
      var tooLong =
          Resources.text(AuthorityCertificateTest.class, "common-name--65-characters.txt").strip();

      assertThat(
              AuthorityCertificate.create(
                      keys, SignatureScheme.SHA1_WITH_ECDSA, longest, SERIAL, NOT_BEFORE, NOT_AFTER)
                  .der())
          .isNotEmpty();
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  AuthorityCertificate.create(
                      keys,
                      SignatureScheme.SHA1_WITH_ECDSA,
                      tooLong,
                      SERIAL,
                      NOT_BEFORE,
                      NOT_AFTER))
          .withMessageContaining("1 to 64 characters");
    }

    @ParameterizedTest(name = "serial {0}")
    @ValueSource(longs = {0, -1})
    @DisplayName("a serial number that is not positive")
    void aSerialThatIsNotPositive(long serial) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  AuthorityCertificate.create(
                      keys,
                      SignatureScheme.SHA1_WITH_ECDSA,
                      COMMON_NAME,
                      BigInteger.valueOf(serial),
                      NOT_BEFORE,
                      NOT_AFTER))
          .withMessageContaining("positive");
    }
  }
}
