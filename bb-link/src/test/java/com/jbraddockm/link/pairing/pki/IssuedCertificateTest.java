package com.jbraddockm.link.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link IssuedCertificate}: a leaf the authority signs for another key — the missing
 * CA→leaf primitive behind the authority-as-hub model.
 */
@DisplayName("IssuedCertificate: a leaf signed by the pairing authority")
class IssuedCertificateTest {

  private static final String ISSUER_CN = "PC issuer 0000000000000000";
  private static final String SUBJECT_CN = "control";
  private static final BigInteger SERIAL = BigInteger.valueOf(0x2222);
  private static final Instant NOT_BEFORE = Instant.parse("2026-10-11T10:00:00Z");
  private static final Instant NOT_AFTER = Instant.parse("2036-10-09T10:00:00Z");

  private final KeyPair issuerKeys = TestKeys.ec(); // the authority (P-521), as in pairing
  private KeyPair subjectKeys; // a distinct leaf key
  private X509Certificate leaf;

  @BeforeEach
  void issueALeaf() throws Exception {
    subjectKeys = generateEc();
    leaf =
        CertificateDetails.certificate(
            IssuedCertificate.create(
                    subjectKeys.getPublic(),
                    SUBJECT_CN,
                    issuerKeys.getPublic(),
                    ISSUER_CN,
                    issuerKeys.getPrivate(),
                    SignatureScheme.SHA1_WITH_ECDSA,
                    SERIAL,
                    NOT_BEFORE,
                    NOT_AFTER)
                .der());
  }

  @Nested
  @DisplayName("read as a certificate")
  class AsX509 {

    @Test
    @DisplayName("is a version 3 leaf with the serial and validity it was given")
    void hasWhatItWasGiven() {
      assertThat(leaf.getVersion()).isEqualTo(3);
      assertThat(leaf.getSerialNumber()).isEqualTo(SERIAL);
      assertThat(leaf.getNotBefore().toInstant()).isEqualTo(NOT_BEFORE);
      assertThat(leaf.getNotAfter().toInstant()).isEqualTo(NOT_AFTER);
    }

    @Test
    @DisplayName("carries the subject's key and the subject's name, issued by the authority")
    void subjectAndIssuer() {
      assertThat(leaf.getPublicKey()).isEqualTo(subjectKeys.getPublic());
      assertThat(leaf.getSubjectX500Principal().getName()).isEqualTo("CN=" + SUBJECT_CN);
      assertThat(leaf.getIssuerX500Principal().getName()).isEqualTo("CN=" + ISSUER_CN);
      assertThat(leaf.getIssuerX500Principal()).isNotEqualTo(leaf.getSubjectX500Principal());
    }

    @Test
    @DisplayName("is signed by the issuer's key, not its own: the chain verifies, self-verify fails")
    void signedByTheIssuer() {
      assertThatCode(() -> leaf.verify(issuerKeys.getPublic())).doesNotThrowAnyException();
      assertThatThrownBy(() -> leaf.verify(subjectKeys.getPublic())).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("is not a certificate authority")
    void isNotACa() {
      assertThat(leaf.getBasicConstraints()).isEqualTo(-1);
    }

    @Test
    @DisplayName("has a TLS-client key usage: digital signature and key encipherment only, critical")
    void keyUsage() {
      // getKeyUsage: [digitalSignature, nonRepudiation, keyEncipherment, dataEncipherment, …]
      assertThat(leaf.getKeyUsage())
          .containsExactly(true, false, true, false, false, false, false, false, false);
      assertThat(leaf.getCriticalExtensionOIDs()).contains("2.5.29.15");
    }

    @Test
    @DisplayName(
        "names the subject key by its own SHA-1, but the authority key identifier is the ISSUER's"
            + " SHA-1 — the link to the enrolled authority")
    void keyIdentifiers() throws Exception {
      var sha1 = MessageDigest.getInstance("SHA-1");
      var subjectKeyId = sha1.digest(subjectKeys.getPublic().getEncoded());
      var issuerKeyId = sha1.digest(issuerKeys.getPublic().getEncoded());

      assertThat(leaf.getExtensionValue("2.5.29.14"))
          .isEqualTo(Der.octetString(Der.octetString(subjectKeyId)));
      assertThat(leaf.getExtensionValue("2.5.29.35"))
          .isEqualTo(
              Der.octetString(
                  Der.sequence(Der.concatenate(new byte[] {(byte) 0x80, 0x14}, issuerKeyId))));
    }

    @Test
    @DisplayName("can be issued by an RSA authority with an RSA scheme too")
    void worksWithRsa() throws Exception {
      var rsaIssuer = TestKeys.rsa();
      var rsaSubject = TestKeys.otherRsa();
      var made =
          IssuedCertificate.create(
              rsaSubject.getPublic(),
              SUBJECT_CN,
              rsaIssuer.getPublic(),
              ISSUER_CN,
              rsaIssuer.getPrivate(),
              SignatureScheme.SHA256_WITH_RSA,
              SERIAL,
              NOT_BEFORE,
              NOT_AFTER);

      var read = CertificateDetails.certificate(made.der());

      assertThat(read.getSigAlgName()).isEqualTo("SHA256withRSA");
      assertThat(read.getPublicKey()).isEqualTo(rsaSubject.getPublic());
      assertThatCode(() -> read.verify(rsaIssuer.getPublic())).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("how it is written")
  class Writing {

    @Test
    @DisplayName("pem is a CERTIFICATE block of the DER, which round-trips through CertificateDetails")
    void pemAndDer() throws Exception {
      var issued =
          IssuedCertificate.create(
              subjectKeys.getPublic(),
              SUBJECT_CN,
              issuerKeys.getPublic(),
              ISSUER_CN,
              issuerKeys.getPrivate(),
              SignatureScheme.SHA1_WITH_ECDSA,
              SERIAL,
              NOT_BEFORE,
              NOT_AFTER);

      assertThat(issued.pem()).startsWith("-----BEGIN CERTIFICATE-----\n");
      assertThat(CertificateDetails.firstCertificate(issued.pem()).getSerialNumber())
          .isEqualTo(SERIAL);
    }
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusals {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "   "})
    @DisplayName("a blank subject common name")
    void aBlankSubject(String blank) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> issue(blank, ISSUER_CN, SERIAL))
          .withMessageContaining("subject")
          .withMessageContaining("1 to 64 characters");
    }

    @Test
    @DisplayName("a blank issuer common name")
    void aBlankIssuer() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> issue(SUBJECT_CN, "  ", SERIAL))
          .withMessageContaining("issuer")
          .withMessageContaining("1 to 64 characters");
    }

    @Test
    @DisplayName("a common name of 65 characters, though 64 is allowed")
    void aNameOf65() throws Exception {
      assertThat(issue(SUBJECT_CN.repeat(1) + "a".repeat(57), ISSUER_CN, SERIAL)).isNotNull();
      assertThatIllegalArgumentException().isThrownBy(() -> issue("a".repeat(65), ISSUER_CN, SERIAL));
    }

    @ParameterizedTest(name = "serial {0}")
    @ValueSource(longs = {0, -1})
    @DisplayName("a serial number that is not positive")
    void aSerialThatIsNotPositive(long serial) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> issue(SUBJECT_CN, ISSUER_CN, BigInteger.valueOf(serial)))
          .withMessageContaining("positive");
    }

    private IssuedCertificate issue(String subjectCn, String issuerCn, BigInteger serial)
        throws Exception {
      return IssuedCertificate.create(
          subjectKeys.getPublic(),
          subjectCn,
          issuerKeys.getPublic(),
          issuerCn,
          issuerKeys.getPrivate(),
          SignatureScheme.SHA1_WITH_ECDSA,
          serial,
          NOT_BEFORE,
          NOT_AFTER);
    }
  }

  private static KeyPair generateEc() throws Exception {
    var generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp521r1"));
    return generator.generateKeyPair();
  }
}
