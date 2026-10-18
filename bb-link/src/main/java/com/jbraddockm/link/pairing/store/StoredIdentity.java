package com.jbraddockm.link.pairing.store;

import com.jbraddockm.core.json.Json;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

/**
 * What is kept beside the key and the certificate.
 *
 * @param commonName the common name that the request had
 * @param createdAt when the identity was made
 */
public record StoredIdentity(String commonName, Instant createdAt) {

  /**
   * Checks the components.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public StoredIdentity {
    Objects.requireNonNull(commonName, "commonName");
    Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * The text of the file.
   *
   * @return JSON
   */
  public String toJsonText() {
    return Json.write(Json.object("commonName", commonName, "createdAt", createdAt.toString()))
        + "\n";
  }

  /**
   * Reads the text of the file. A file that an earlier version wrote has an identifier of the
   * device too, which is ignored.
   *
   * @param text JSON as {@link #toJsonText()} writes it
   * @return the identity
   * @throws IllegalArgumentException if it is not that
   */
  public static StoredIdentity fromJsonText(String text) {
    try {
      if (Json.parse(text) instanceof Map<?, ?> fields
          && fields.get("commonName") instanceof String commonName
          && fields.get("createdAt") instanceof String createdAt) {
        return new StoredIdentity(commonName, Instant.parse(createdAt));
      }
    } catch (Json.JsonException | DateTimeParseException failure) {
      throw new IllegalArgumentException(
          "the identity file is not readable: " + failure.getMessage());
    }
    throw new IllegalArgumentException("the identity file lacks commonName or createdAt");
  }
}
