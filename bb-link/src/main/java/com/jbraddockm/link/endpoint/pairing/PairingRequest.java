package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What a pairing is asked to do.
 *
 * @param confirmPin the PIN of the device, typed by the caller; the pairing stops if the device
 *     says another
 * @param devicesDirectory the folder that holds one folder for each device; the pairing goes in
 *     {@code <devicesDirectory>/<PIN>/}
 * @param renew whether this is a renewal of an existing pairing: it requires a pairing to be on
 *     disk, reuses that pairing's identifier, and refreshes the enrollment (reaffirming the stored
 *     certificate when it is still there, re-minting it when it is not). A plain pairing ({@code
 *     false}) is for a device that is not paired yet and refuses when one is already on disk.
 */
public record PairingRequest(String confirmPin, Path devicesDirectory, boolean renew) {

  /**
   * Checks the components.
   *
   * @throws InvalidArgumentException naming the parameter that is wrong
   */
  public PairingRequest {
    if (confirmPin == null || confirmPin.isBlank()) {
      throw new InvalidArgumentException("confirmPin", "is required");
    }
    Objects.requireNonNull(devicesDirectory, "devicesDirectory");
  }
}
