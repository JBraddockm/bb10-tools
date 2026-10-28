package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;

/**
 * What a local pairing removal did. The removal is local only: the keys and certificates are deleted
 * but {@code pairing.json} is kept, and the device is told nothing and keeps the pairing. The result
 * carries a note telling the caller to remove this computer on the device.
 *
 * @param pin the device folder whose certificates were removed
 * @param deviceId the identifier this computer gave itself, kept for a later re-pair; {@code null} if
 *     the pairing had no readable metadata
 * @param directory the device folder (kept, now holding only {@code pairing.json})
 */
public record RemovalResult(String pin, String deviceId, Path directory) implements CommandResult {

  /** The guidance returned to the caller: the device still has to be removed by hand. */
  static final String NOTE =
      "Removed this computer's keys and certificates for the device. The identifier is kept so a "
          + "later re-pair reuses it (no duplicate on the device). The device still lists this computer "
          + "in Device Connections; remove it there on the device (Settings > Device Connections) — "
          + "bb-link cannot unpair over the network.";

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pin,
        "deviceId", deviceId,
        "directory", directory.toString(),
        "identityKept", Boolean.TRUE,
        "deviceStillPaired", Boolean.TRUE,
        "note", NOTE);
  }
}
