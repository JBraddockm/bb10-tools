package com.jbraddockm.link.endpoint.pairing.store;

import com.jbraddockm.core.json.Json;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * What is kept of a pairing besides the two authorities and the key.
 *
 * @param deviceId the identifier that the computer gave itself
 * @param commonName the common name of the computer's authority
 * @param createdAt when the pairing was made
 */
public record StoredPairing(String deviceId, String commonName, Instant createdAt) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public StoredPairing {
    Objects.requireNonNull(deviceId, "deviceId");
    Objects.requireNonNull(commonName, "commonName");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * The text of the file.
   *
   * @return JSON
   */
  public String toJsonText() {
    return Json.write(
            Json.object(
                "deviceId", deviceId, "commonName", commonName, "createdAt", createdAt.toString()))
        + "\n";
  }

  /**
   * Reads the text of the file.
   *
   * @param text JSON as {@link #toJsonText()} writes it
   * @return the pairing
   * @throws IllegalArgumentException if it is not that
   */
  public static StoredPairing fromJsonText(String text) {
    try {
      if (Json.parse(text) instanceof Map<?, ?> fields
          && fields.get("deviceId") instanceof String deviceId
          && fields.get("commonName") instanceof String commonName
          && fields.get("createdAt") instanceof String createdAt) {
        return new StoredPairing(deviceId, commonName, Instant.parse(createdAt));
      }
    } catch (Json.JsonException | DateTimeParseException failure) {
      throw new IllegalArgumentException(
          "the pairing file is not readable: " + failure.getMessage());
    }
    throw new IllegalArgumentException("the pairing file lacks deviceId, commonName or createdAt");
  }
}
