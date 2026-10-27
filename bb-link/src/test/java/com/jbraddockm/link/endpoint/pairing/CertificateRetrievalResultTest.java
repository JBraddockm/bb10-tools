package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.endpoint.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CertificateRetrievalResult}. */
@DisplayName("CertificateRetrievalResult: the certificate that was kept")
class CertificateRetrievalResultTest {

  private static final Path FOLDER = Path.of("devices", "2A3B4C5D");

  @Test
  @DisplayName(
      "toJson gives the PIN, the name, the three paths, the certificate as the report describes it,"
          + " and the verification")
  void toJsonGivesTheFields() throws Exception {
    var details =
        CertificateDetails.of(
            CertificateDetails.firstCertificate(
                Resources.text(TestKeys.class, "certificate--device-issued.pem")));
    var issuedAt = details.notAfter().minus(Duration.ofDays(30));
    var result =
        new CertificateRetrievalResult(
            "2A3B4C5D",
            "BBCGIAUTH [2A3B4C5D] workstation",
            FOLDER,
            FOLDER.resolve("client_cert.pem"),
            FOLDER.resolve("client_key.pem"),
            details,
            Verification.verified(),
            issuedAt);

    var json = result.toJson();

    assertThat(json.keySet())
        .containsExactly(
            "pin",
            "commonName",
            "directory",
            "certificateFile",
            "keyFile",
            "certificate",
            "verification");
    assertThat(json)
        .containsEntry("pin", "2A3B4C5D")
        .containsEntry("commonName", "BBCGIAUTH [2A3B4C5D] workstation")
        .containsEntry("directory", FOLDER.toString())
        .containsEntry("certificateFile", FOLDER.resolve("client_cert.pem").toString())
        .containsEntry("keyFile", FOLDER.resolve("client_key.pem").toString())
        .containsEntry("certificate", CertificateReport.describe(details, issuedAt))
        .containsEntry("verification", Verification.verified().toJson());
    assertThat(((Map<?, ?>) json.get("certificate")).get("daysLeft")).isEqualTo(30L);
  }

  @Test
  @DisplayName("toJson never holds the private key, only the path of its file")
  void toJsonHoldsNoKey() throws Exception {
    var details =
        CertificateDetails.of(
            CertificateDetails.firstCertificate(
                Resources.text(TestKeys.class, "certificate--device-issued.pem")));

    var json =
        new CertificateRetrievalResult(
                "2A3B4C5D",
                "x",
                FOLDER,
                FOLDER.resolve("c.pem"),
                FOLDER.resolve("k.pem"),
                details,
                Verification.skipped(),
                details.notBefore())
            .toJson();

    assertThat(json.toString()).doesNotContain("PRIVATE KEY");
  }
}
