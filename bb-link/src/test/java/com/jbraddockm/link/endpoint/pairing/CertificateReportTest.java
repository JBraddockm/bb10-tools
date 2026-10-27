package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.endpoint.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CertificateReport}: the JSON report of a certificate file. */
@DisplayName("CertificateReport: what a certificate file says, as JSON")
class CertificateReportTest {

  private static final Path FILE = Path.of("devices", "2A3B4C5D", "client_cert.pem");

  private Map<?, ?> expected;
  private CertificateDetails details;

  @BeforeEach
  void readTheCertificate() throws Exception {
    expected =
        (Map<?, ?>)
            Json.parse(Resources.text(TestKeys.class, "certificate--device-issued--expected.json"));
    details =
        CertificateDetails.of(
            CertificateDetails.firstCertificate(
                Resources.text(TestKeys.class, "certificate--device-issued.pem")));
  }

  @Test
  @DisplayName("toJson gives the file first, then the fields of the certificate, in a fixed order")
  void toJsonGivesTheFieldsInOrder() {
    var json =
        new CertificateReport(FILE, details, details.notAfter().minus(Duration.ofDays(10)))
            .toJson();

    assertThat(json.keySet())
        .containsExactly(
            "file",
            "subject",
            "issuer",
            "serialNumber",
            "notBefore",
            "notAfter",
            "daysLeft",
            "expired",
            "sha256Fingerprint",
            "publicKeyAlgorithm",
            "publicKeyBits",
            "signatureAlgorithm",
            "certificateAuthority");
  }

  @Test
  @DisplayName(
      "toJson gives what the certificate says, with dates as ISO instants and the serial number as"
          + " text")
  void toJsonGivesWhatTheCertificateSays() {
    var json =
        new CertificateReport(FILE, details, details.notAfter().minus(Duration.ofDays(10)))
            .toJson();

    assertThat(json.get("file")).isEqualTo(FILE.toString());
    assertThat(json.get("subject")).isEqualTo(expected.get("subject"));
    assertThat(json.get("issuer")).isEqualTo(expected.get("issuer"));
    assertThat(json.get("serialNumber")).isEqualTo(expected.get("serialNumber"));
    assertThat(json.get("notBefore")).isEqualTo(expected.get("notBefore"));
    assertThat(json.get("notAfter")).isEqualTo(expected.get("notAfter"));
    assertThat(json.get("sha256Fingerprint")).isEqualTo(expected.get("sha256Fingerprint"));
    assertThat(json.get("publicKeyAlgorithm")).isEqualTo(expected.get("publicKeyAlgorithm"));
    assertThat(json.get("publicKeyBits")).isEqualTo(2048);
    assertThat(json.get("signatureAlgorithm")).isEqualTo(expected.get("signatureAlgorithm"));
    assertThat(json.get("certificateAuthority")).isEqualTo(false);
  }

  @Test
  @DisplayName("counts the days left and says that it has not expired, before the end")
  void countsTheDaysLeft() {
    var json =
        new CertificateReport(FILE, details, details.notAfter().minus(Duration.ofDays(10)))
            .toJson();

    assertThat(json).containsEntry("daysLeft", 10L).containsEntry("expired", false);
  }

  @Test
  @DisplayName("counts the days after the end as negative and says that it has expired")
  void countsTheDaysAfterTheEnd() {
    var json =
        new CertificateReport(FILE, details, details.notAfter().plus(Duration.ofDays(2))).toJson();

    assertThat(json).containsEntry("daysLeft", -2L).containsEntry("expired", true);
  }

  @Test
  @DisplayName("describe is what toJson says of the certificate, without the file")
  void describeIsTheCertificatePartOfToJson() {
    var now = details.notAfter().minus(Duration.ofDays(5));

    var described = CertificateReport.describe(details, now);

    assertThat(described).doesNotContainKey("file");
    assertThat(new CertificateReport(FILE, details, now).toJson()).containsAllEntriesOf(described);
  }
}
