package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What a certificate file says, and how long it has left.
 *
 * @param file the file that was read
 * @param details what the certificate says
 * @param checkedAt the moment that the days left are counted from
 */
record CertificateReport(Path file, CertificateDetails details, Instant checkedAt)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("file", file.toString());
    fields.putAll(describe(details, checkedAt));
    return fields;
  }

  /**
   * The fields that describe a certificate, in the order that a reader looks for them.
   *
   * @param details the certificate
   * @param now the moment to count the days left from
   * @return the fields, as JSON values
   */
  static Map<String, Object> describe(CertificateDetails details, Instant now) {
    return Json.object(
        "subject", details.subject(),
        "issuer", details.issuer(),
        "serialNumber", details.serialNumber().toString(),
        "notBefore", details.notBefore().toString(),
        "notAfter", details.notAfter().toString(),
        "daysLeft", details.daysLeft(now),
        "expired", details.expiredAt(now),
        "sha256Fingerprint", details.sha256Fingerprint(),
        "publicKeyAlgorithm", details.publicKeyAlgorithm(),
        "publicKeyBits", details.publicKeyBits(),
        "signatureAlgorithm", details.signatureAlgorithm(),
        "certificateAuthority", details.certificateAuthority());
  }
}
