package com.jbraddockm.output;

import java.util.Map;
import java.util.Objects;

/**
 * A failure that the output itself reveals although the tool exited normally.
 *
 * @param message a human-readable English description
 * @param details additional data about the failure, possibly empty; the iteration order is
 *     preserved
 */
public record CommandFailure(String message, Map<String, Object> details) {

  /**
   * Creates a failure and takes an immutable, order-preserving copy of the details.
   *
   * @throws NullPointerException if {@code message} or {@code details} is {@code null}
   */
  public CommandFailure {
    Objects.requireNonNull(message, "message");
    details = OutputText.orderedCopy(Objects.requireNonNull(details, "details"));
  }
}
