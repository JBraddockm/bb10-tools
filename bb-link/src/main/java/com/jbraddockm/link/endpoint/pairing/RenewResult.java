package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.time.Instant;
import java.util.Map;

/**
 * What a renewal did: the re-established pairing and the refreshed port 8443 login certificate.
 *
 * @param pairing the pairing that was reaffirmed or re-minted
 * @param certificate the refreshed device-signed login certificate
 * @param renewedAt when the renewal finished
 */
record RenewResult(PairingResult pairing, CertificateRetrievalResult certificate, Instant renewedAt)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pairing.pin(),
        "deviceId", pairing.deviceId(),
        "renewedAt", renewedAt.toString(),
        "pairing", pairing.toJson(),
        "certificate", certificate.toJson());
  }
}
