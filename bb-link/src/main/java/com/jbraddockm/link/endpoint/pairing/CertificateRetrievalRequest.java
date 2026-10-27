package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What an issuance is asked to do.
 *
 * @param confirmPin the PIN of the device, typed by the caller; the issuance stops if the device
 *     says another
 * @param devicesDirectory the folder that holds one folder for each device; the identity goes in
 *     {@code <devicesDirectory>/<PIN>/}
 * @param hostName the name of this computer for the certificate; this computer's if it is empty
 * @param replace whether to replace an identity that is on disk
 * @param verify whether to try the new certificate by logging in with it
 * @param verifyPort the port that takes certificates, for that trial
 */
public record CertificateRetrievalRequest(
    String confirmPin,
    Path devicesDirectory,
    String hostName,
    boolean replace,
    boolean verify,
    int verifyPort) {

  /**
   * Checks and normalises the components.
   *
   * @throws InvalidArgumentException naming the parameter that is wrong
   */
  public CertificateRetrievalRequest {
    if (confirmPin == null || confirmPin.isBlank()) {
      throw new InvalidArgumentException("confirmPin", "is required");
    }
    Objects.requireNonNull(devicesDirectory, "devicesDirectory");
    hostName = notBlank("hostName", hostName);
    if (verifyPort < 1 || verifyPort > 65_535) {
      throw new InvalidArgumentException("verifyPort", "must be a port, 1 to 65535");
    }
  }

  private static String notBlank(String parameter, String value) {
    if (value != null && value.isBlank()) {
      throw new InvalidArgumentException(parameter, "must not be blank");
    }
    return value != null ? value.strip() : null;
  }
}
