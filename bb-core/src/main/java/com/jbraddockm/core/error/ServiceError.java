package com.jbraddockm.core.error;

import com.jbraddockm.core.json.Json;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * A failure together with the data a caller needs to react to it.
 *
 * @param code the stable identifier to branch on
 * @param message a human-readable English description; its wording is not stable
 * @param details additional code-specific data, possibly empty; the iteration order is preserved
 */
public record ServiceError(ErrorCode code, String message, Map<String, Object> details) {

  /**
   * Creates an error and takes an immutable, order-preserving copy of the details.
   *
   * @throws NullPointerException if {@code code} or {@code message} is {@code null}
   */
  public ServiceError {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    details =
        Collections.unmodifiableSequencedMap(
            new LinkedHashMap<>(details == null ? Map.of() : details));
  }

  /**
   * Creates an error without details.
   *
   * @param code the stable identifier to branch on
   * @param message a human-readable English description
   */
  public ServiceError(ErrorCode code, String message) {
    this(code, message, Map.of());
  }

  /**
   * Describes an unexpected exception as {@link CoreErrorCode#INTERNAL}.
   *
   * @param cause the unexpected exception
   * @return an error carrying the exception's message and class name
   */
  public static ServiceError internal(Throwable cause) {
    var message =
        cause.getLocalizedMessage() != null
            ? cause.getLocalizedMessage()
            : cause.getClass().getSimpleName();
    return new ServiceError(
        CoreErrorCode.INTERNAL, message, Map.of("exception", cause.getClass().getName()));
  }

  /**
   * Returns the JSON-compatible representation used on the wire. Empty details are omitted.
   *
   * @return an insertion-ordered map of {@code code}, {@code message} and, if present, {@code
   *     details}
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "code", code.wireName(), "message", message, "details", details.isEmpty() ? null : details);
  }
}
