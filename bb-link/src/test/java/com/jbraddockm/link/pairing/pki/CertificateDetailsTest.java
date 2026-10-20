package com.jbraddockm.link.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.Resources;
import java.math.BigInteger;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link CertificateDetails}: describing a certificate and finding one in a text. */
@DisplayName("CertificateDetails: what a certificate says, and where to find it in a text")
class CertificateDetailsTest {

  private static String resource(String name) {
    return Resources.text(CertificateDetailsTest.class, name);
  }

  private static CertificateDetails detailsOf(String name) throws Exception {
    return CertificateDetails.of(CertificateDetails.firstCertificate(resource(name)));
  }

  @Nested
  @DisplayName("of")
  class Details {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "certificate--device-ca",
          "certificate--device-issued",
          "certificate--device-authority-ec"
        })
    @DisplayName(
        "gives what OpenSSL says of the certificate: names, serial, validity, fingerprint, key,"
            + " signature, authority")
    void givesWhatOpenSslSays(String name) throws Exception {
      var expected = (Map<?, ?>) Json.parse(resource(name + "--expected.json"));

      var details = detailsOf(name + ".pem");

      assertThat(details.subject()).isEqualTo(expected.get("subject"));
      assertThat(details.issuer()).isEqualTo(expected.get("issuer"));
      assertThat(details.serialNumber())
          .isEqualTo(new BigInteger((String) expected.get("serialNumber")));
      assertThat(details.notBefore()).isEqualTo(Instant.parse((String) expected.get("notBefore")));
      assertThat(details.notAfter()).isEqualTo(Instant.parse((String) expected.get("notAfter")));
      assertThat(details.sha256Fingerprint()).isEqualTo(expected.get("sha256Fingerprint"));
      assertThat(details.publicKeyAlgorithm()).isEqualTo(expected.get("publicKeyAlgorithm"));
      assertThat(details.publicKeyBits())
          .isEqualTo(((Long) expected.get("publicKeyBits")).intValue());
      assertThat(details.signatureAlgorithm()).isEqualTo(expected.get("signatureAlgorithm"));
      assertThat(details.certificateAuthority()).isEqualTo(expected.get("certificateAuthority"));
    }

    @Test
    @DisplayName(
        "writes the attribute 2.5.4.5 as SERIALNUMBER and 2.5.4.13 as DESCRIPTION, in the order of"
            + " the certificate")
    void namesTheAttributesOfADevicesCertificate() throws Exception {
      var expected = resource("certificate--with-description--expected-subject.txt").strip();

      assertThat(detailsOf("certificate--with-description.pem").subject()).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("daysLeft and expiredAt")
  class Validity {

    private CertificateDetails details;

    @BeforeEach
    void readTheCertificate() throws Exception {
      details = detailsOf("certificate--device-issued.pem");
    }

    @Test
    @DisplayName("daysLeft counts whole days to the end of the validity")
    void daysLeftCountsWholeDays() {
      var end = details.notAfter();

      assertThat(details.daysLeft(end.minus(Duration.ofDays(37)))).isEqualTo(37);
      assertThat(details.daysLeft(end.minus(Duration.ofHours(36)))).isEqualTo(1);
      assertThat(details.daysLeft(end.minus(Duration.ofHours(23)))).isZero();
      assertThat(details.daysLeft(end)).isZero();
    }

    @Test
    @DisplayName("daysLeft is negative after the end")
    void daysLeftIsNegativeAfterTheEnd() {
      assertThat(details.daysLeft(details.notAfter().plus(Duration.ofDays(3)))).isEqualTo(-3);
    }

    @Test
    @DisplayName("expiredAt is true after the end, and not at the end, nor before the start")
    void expiredAtIsTrueAfterTheEnd() {
      assertThat(details.expiredAt(details.notAfter().plusSeconds(1))).isTrue();
      assertThat(details.expiredAt(details.notAfter())).isFalse();
      assertThat(details.expiredAt(details.notBefore().minusSeconds(1))).isFalse();
    }
  }

  @Nested
  @DisplayName("firstCertificate")
  class FirstCertificate {

    @Test
    @DisplayName("takes the first of several certificates in a text")
    void takesTheFirst() throws Exception {
      var first =
          CertificateDetails.firstCertificate(resource("certificate--two-certificates.pem"));

      assertThat(CertificateDetails.of(first).subject()).startsWith("CN=BBCGIAUTH");
    }

    @Test
    @DisplayName("finds a certificate among the text that an export puts around it")
    void findsACertificateAmongText() throws Exception {
      assertThat(detailsOf("certificate--with-noise.txt").serialNumber())
          .isEqualTo(BigInteger.valueOf(4097));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pem--no-block.txt", "certificate--bare-text.txt"})
    @DisplayName("fails if the text has no CERTIFICATE block, whatever other blocks it has")
    void failsWithoutACertificateBlock(String name) {
      var text = resource(name);

      assertThatThrownBy(() -> CertificateDetails.firstCertificate(text))
          .isInstanceOf(CertificateException.class)
          .hasMessage("the text has no CERTIFICATE block");
    }

    @Test
    @DisplayName("fails, and says so, if the block is not base64")
    void failsForBadBase64() {
      var text = resource("pem--bad-base64.txt");

      assertThatThrownBy(() -> CertificateDetails.firstCertificate(text))
          .isInstanceOf(CertificateException.class)
          .hasMessage("the CERTIFICATE block is not base64")
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("fails if the block is base64 but is not a certificate")
    void failsForADerThatIsNotACertificate() {
      var text = resource("certificate--not-a-certificate.pem");

      assertThatThrownBy(() -> CertificateDetails.firstCertificate(text))
          .isInstanceOf(CertificateException.class);
    }
  }

  @Test
  @DisplayName("certificate reads the DER of a certificate, and fails for bytes that are not one")
  void certificateReadsDer() throws Exception {
    var der =
        Pem.first(resource("certificate--device-ca.pem"), "CERTIFICATE").orElseThrow().content();

    assertThat(CertificateDetails.certificate(der).getSerialNumber())
        .isEqualTo(
            CertificateDetails.firstCertificate(resource("certificate--device-ca.pem"))
                .getSerialNumber());
    assertThatThrownBy(() -> CertificateDetails.certificate(new byte[] {1, 2, 3}))
        .isInstanceOf(CertificateException.class);
  }
}
