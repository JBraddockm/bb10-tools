package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredPairing;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Forgets a device pairing on this computer: deletes the keys and certificates in {@code
 * <devices>/<PIN>/} but keeps its {@code pairing.json}, so the identifier this computer gave itself is
 * preserved and a later re-pair reuses it (the device then updates its one entry rather than adding a
 * duplicate). Purely local — it sends nothing to the device, which keeps the pairing until it is
 * removed there. This is what BlackBerry Link's "remove" does; the device has no endpoint to unpair
 * over the network (its pairing.cgi only enrols — its delete path is dead code, verified).
 */
public final class RemovePairing {

  private RemovePairing() {}

  /**
   * Deletes the local pairing folder for {@code pin}.
   *
   * @param devices the folder that holds one folder per device
   * @param pin the device folder to forget
   * @return the removal, or a failure if there is no such pairing or it cannot be deleted
   */
  public static CommandOutcome<RemovalResult> remove(Path devices, String pin) {
    var pairing = PairingDirectory.of(devices, pin);
    if (!pairing.exists()) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.INVALID_ARGUMENT,
              "there is no local pairing for " + pin + " in " + devices));
    }
    String deviceId = readDeviceId(pairing);
    try {
      pairing.forget();
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "could not delete " + pairing.directory() + ": " + failure.getMessage()));
    }
    return CommandOutcome.succeeded(new RemovalResult(pin, deviceId, pairing.directory()));
  }

  /** The stored identifier, read before deleting; {@code null} if the metadata is missing or bad. */
  private static String readDeviceId(PairingDirectory pairing) {
    try {
      return pairing.metadata().map(StoredPairing::deviceId).orElse(null);
    } catch (IOException | RuntimeException unreadable) {
      return null;
    }
  }
}
