package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateException;
import java.time.Clock;

/**
 * Reads a certificate file and says what is in it and how long it has left. It needs no session and
 * no device, and sends nothing anywhere.
 */
public final class InspectCertificate {

  private static final long MAXIMUM_BYTES = 1024 * 1024;

  private InspectCertificate() {}

  /**
   * Reads the first certificate of a PEM file.
   *
   * @param file the file
   * @param clock the clock to count the days left by
   * @return the report, or an error that names the file
   */
  public static CommandOutcome<CertificateReport> inspect(Path file, Clock clock) {
    try {
      if (!Files.isRegularFile(file)) {
        return invalid("is not a file");
      }
      if (Files.size(file) > MAXIMUM_BYTES) {
        return invalid("is larger than 1 MiB");
      }
      var certificate =
          CertificateDetails.firstCertificate(Files.readString(file, StandardCharsets.UTF_8));
      return CommandOutcome.succeeded(
          new CertificateReport(file, CertificateDetails.of(certificate), clock.instant()));
    } catch (CertificateException failure) {
      return invalid("has no readable certificate: " + failure.getMessage());
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR, "Could not read " + file + ": " + failure.getMessage()));
    }
  }

  private static CommandOutcome<CertificateReport> invalid(String reason) {
    return CommandOutcome.failed(new InvalidArgumentException("file", reason).toServiceError());
  }
}
