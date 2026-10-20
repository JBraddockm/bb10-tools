package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link InspectCertificate}: reading the first certificate of a file and reporting its
 * validity.
 */
@DisplayName("InspectCertificate: what a certificate file says")
class InspectCertificateTest {

  private static final long ONE_MEBIBYTE = 1024 * 1024;

  @TempDir private Path folder;

  private Clock clockAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  @Test
  @DisplayName(
      "reads the certificate in a file: the file, what it says, and the time it was read at")
  void readsTheCertificate() throws Exception {
    var file = Resources.copyTo(TestKeys.class, "certificate--device-issued.pem", folder);
    var details =
        CertificateDetails.of(CertificateDetails.firstCertificate(Files.readString(file)));
    var now = details.notAfter().minusSeconds(86_400L * 10);

    var report = succeeded(InspectCertificate.inspect(file, clockAt(now)));

    assertThat(report.file()).isEqualTo(file);
    assertThat(report.details()).isEqualTo(details);
    assertThat(report.checkedAt()).isEqualTo(now);
    assertThat(report.toJson()).containsEntry("daysLeft", 10L).containsEntry("expired", false);
  }

  @Test
  @DisplayName("reads the first certificate when a file has several")
  void readsTheFirstOfSeveral() throws Exception {
    var file = Resources.copyTo(TestKeys.class, "certificate--two-certificates.pem", folder);

    var report =
        succeeded(InspectCertificate.inspect(file, clockAt(Instant.parse("2026-10-18T00:00:00Z"))));

    assertThat(report.details().subject()).startsWith("CN=BBCGIAUTH");
  }

  @Test
  @DisplayName("says that a certificate has expired when the clock is past its end")
  void saysThatItHasExpired() throws Exception {
    var file = Resources.copyTo(TestKeys.class, "certificate--device-issued.pem", folder);
    var details =
        CertificateDetails.of(CertificateDetails.firstCertificate(Files.readString(file)));

    var report =
        succeeded(
            InspectCertificate.inspect(file, clockAt(details.notAfter().plusSeconds(86_400L * 2))));

    assertThat(report.toJson()).containsEntry("expired", true).containsEntry("daysLeft", -2L);
  }

  @Test
  @DisplayName("refuses a path that is not a file: a folder, or nothing")
  void refusesWhatIsNotAFile() {
    for (var path : new Path[] {folder, folder.resolve("nothing.pem")}) {
      var failure = failed(InspectCertificate.inspect(path, clockAt(Instant.EPOCH)));

      assertThat(failure.error().message()).contains("file").contains("is not a file");
      assertThat(failure.error().details()).containsEntry("param", "file");
    }
  }

  @Test
  @DisplayName(
      "refuses a file larger than 1 MiB, and takes one of exactly 1 MiB as far as its size goes")
  void refusesAFileThatIsTooLarge() throws IOException {
    var tooLarge = Files.write(folder.resolve("large.pem"), new byte[(int) ONE_MEBIBYTE + 1]);
    var exact = Files.write(folder.resolve("exact.pem"), new byte[(int) ONE_MEBIBYTE]);

    assertThat(
            failed(InspectCertificate.inspect(tooLarge, clockAt(Instant.EPOCH))).error().message())
        .contains("is larger than 1 MiB");
    assertThat(failed(InspectCertificate.inspect(exact, clockAt(Instant.EPOCH))).error().message())
        .contains("has no readable certificate");
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(
      strings = {
        "pem--no-block.txt",
        "certificate--bare-text.txt",
        "pem--bad-base64.txt",
        "certificate--not-a-certificate.pem"
      })
  @DisplayName("refuses a file that has no readable certificate, and says why")
  void refusesAFileWithNoCertificate(String resource) {
    var file = Resources.copyTo(TestKeys.class, resource, folder);

    var failure = failed(InspectCertificate.inspect(file, clockAt(Instant.EPOCH)));

    assertThat(failure.error().message()).contains("has no readable certificate: ");
    assertThat(failure.error().details()).containsEntry("param", "file");
  }

  @Test
  @DisplayName("reports a file that cannot be read as text as an error of reading, with the path")
  void reportsAnUnreadableFile() {
    var file = Resources.copyTo(TestKeys.class, "certificate--invalid-utf8.bin", folder);

    var failure = failed(InspectCertificate.inspect(file, clockAt(Instant.EPOCH)));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(failure.error().message()).startsWith("Could not read " + file);
  }
}
