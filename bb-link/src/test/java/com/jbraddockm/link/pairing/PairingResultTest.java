package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link PairingResult}. */
@DisplayName("PairingResult: the pairing that was kept")
class PairingResultTest {

  private static final Path FOLDER = Path.of("devices", "2A3B4C5D");

  @Test
  @DisplayName(
      "toJson gives the identifiers, the paths, and what the two authorities say, and no name of a"
          + " device")
  void toJsonGivesTheFields() throws Exception {
    var authority =
        CertificateDetails.of(
            CertificateDetails.firstCertificate(
                Resources.text(TestKeys.class, "certificate--device-authority-ec.pem")));
    var deviceAuthority =
        CertificateDetails.of(
            CertificateDetails.firstCertificate(
                Resources.text(TestKeys.class, "certificate--device-ca.pem")));
    var pairedAt = deviceAuthority.notAfter().minus(Duration.ofDays(100));
    var result =
        new PairingResult(
            "2A3B4C5D",
            "01AB",
            "PC issuer 0000000000000000",
            FOLDER,
            FOLDER.resolve("authority_cert.pem"),
            FOLDER.resolve("authority_key.pem"),
            FOLDER.resolve("device_authority.pem"),
            authority,
            deviceAuthority,
            pairedAt);

    var json = result.toJson();

    assertThat(json.keySet())
        .containsExactly(
            "pin",
            "deviceId",
            "commonName",
            "directory",
            "authorityFile",
            "keyFile",
            "deviceAuthorityFile",
            "authority",
            "deviceAuthority");
    assertThat(json)
        .containsEntry("pin", "2A3B4C5D")
        .containsEntry("deviceId", "01AB")
        .containsEntry("commonName", "PC issuer 0000000000000000")
        .containsEntry("directory", FOLDER.toString())
        .containsEntry("authorityFile", FOLDER.resolve("authority_cert.pem").toString())
        .containsEntry("keyFile", FOLDER.resolve("authority_key.pem").toString())
        .containsEntry("deviceAuthorityFile", FOLDER.resolve("device_authority.pem").toString())
        .containsEntry("authority", CertificateReport.describe(authority, pairedAt))
        .containsEntry("deviceAuthority", CertificateReport.describe(deviceAuthority, pairedAt));
  }
}
