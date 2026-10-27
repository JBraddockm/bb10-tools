package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * An identity that the device signed a certificate for.
 *
 * @param pin the PIN of the device
 * @param commonName the common name that the request had
 * @param directory the folder of the identity
 * @param certificateFile the certificate that the device returned
 * @param keyFile the private key; never in a result, a log or a trace
 * @param certificate what the certificate says
 * @param verification whether the device took it for a login
 * @param issuedAt the moment that the days left are counted from
 */
record CertificateRetrievalResult(
    String pin,
    String commonName,
    Path directory,
    Path certificateFile,
    Path keyFile,
    CertificateDetails certificate,
    Verification verification,
    Instant issuedAt)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pin,
        "commonName", commonName,
        "directory", directory.toString(),
        "certificateFile", certificateFile.toString(),
        "keyFile", keyFile.toString(),
        "certificate", CertificateReport.describe(certificate, issuedAt),
        "verification", verification.toJson());
  }
}
