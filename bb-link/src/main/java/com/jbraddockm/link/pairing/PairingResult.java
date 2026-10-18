package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;

/**
 * A pairing that the device took.
 *
 * @param pin the PIN of the device
 * @param deviceId the identifier that the computer gave itself
 * @param commonName the common name of the computer's authority
 * @param directory the folder of the pairing
 * @param authorityFile the computer's authority
 * @param keyFile the private key of that authority; never in a result, a log or a trace
 * @param deviceAuthorityFile the authority that the device answered with
 * @param authority what the computer's authority says
 * @param deviceAuthority what the device's authority says none
 * @param pairedAt the moment that the days left are counted from
 */
public record PairingResult(
    String pin,
    String deviceId,
    String commonName,
    Path directory,
    Path authorityFile,
    Path keyFile,
    Path deviceAuthorityFile,
    CertificateDetails authority,
    CertificateDetails deviceAuthority,
    Instant pairedAt)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pin,
        "deviceId", deviceId,
        "commonName", commonName,
        "directory", directory.toString(),
        "authorityFile", authorityFile.toString(),
        "keyFile", keyFile.toString(),
        "deviceAuthorityFile", deviceAuthorityFile.toString(),
        "authority", CertificateReport.describe(authority, pairedAt),
        "deviceAuthority", CertificateReport.describe(deviceAuthority, pairedAt));
  }
}
