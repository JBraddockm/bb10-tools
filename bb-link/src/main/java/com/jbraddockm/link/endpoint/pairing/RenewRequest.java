package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What a renewal is asked to do: re-establish an existing pairing. It re-enrolls the pairing
 * authority under the identifier the device already knows (reaffirming the stored certificate, or
 * re-minting it when it is gone) and refreshes the device-signed certificate for the port 8443
 * login, which is the one that actually expires.
 *
 * @param confirmPin the PIN of the device, typed by the caller; the renewal stops if the device says
 *     another
 * @param devicesDirectory the folder that holds one folder for each device; the pairing is in {@code
 *     <devicesDirectory>/<PIN>/}, and must already be there
 * @param hostName the name of this computer for the refreshed login certificate; this computer's if
 *     it is empty
 * @param verify whether to try the refreshed login certificate by logging in with it
 * @param verifyPort the port that takes certificates, for that trial
 */
public record RenewRequest(
    String confirmPin, Path devicesDirectory, String hostName, boolean verify, int verifyPort) {

  /**
   * Checks the components.
   *
   * @throws InvalidArgumentException naming the parameter that is wrong
   */
  public RenewRequest {
    if (confirmPin == null || confirmPin.isBlank()) {
      throw new InvalidArgumentException("confirmPin", "is required");
    }
    Objects.requireNonNull(devicesDirectory, "devicesDirectory");
    if (verifyPort < 1 || verifyPort > 65_535) {
      throw new InvalidArgumentException("verifyPort", "must be a port, 1 to 65535");
    }
  }
}
