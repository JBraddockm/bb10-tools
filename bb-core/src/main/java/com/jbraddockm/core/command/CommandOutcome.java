package com.jbraddockm.core.command;

import com.jbraddockm.core.error.ServiceError;
import java.util.List;
import java.util.Optional;

/**
 * How a command ended.
 *
 * @param <T> the type of result a successful command produces
 */
public sealed interface CommandOutcome<T extends CommandResult> {

  /**
   * Returns the unprocessed device output, scrubbed of credentials, in the order it was received.
   *
   * @return one entry per exchange; possibly empty
   */
  List<String> raw();

  /**
   * Creates a successful outcome without raw output.
   *
   * @param result the result
   * @param <T> the result type
   * @return the outcome
   */
  static <T extends CommandResult> CommandOutcome<T> succeeded(T result) {
    return new Succeeded<>(result, List.of());
  }

  /**
   * Creates a failed outcome without a partial result or raw output.
   *
   * @param error what went wrong
   * @param <T> the result type
   * @return the outcome
   */
  static <T extends CommandResult> CommandOutcome<T> failed(ServiceError error) {
    return new Failed<>(error, Optional.empty(), List.of());
  }

  /**
   * The command succeeded.
   *
   * @param result the typed result
   * @param raw the scrubbed device output
   * @param <T> the result type
   */
  record Succeeded<T extends CommandResult>(T result, List<String> raw)
      implements CommandOutcome<T> {

    /** Takes an immutable copy of the raw output. */
    public Succeeded {
      raw = List.copyOf(raw);
    }
  }

  /**
   * The command failed.
   *
   * @param error what went wrong
   * @param partialResult what was achieved before the failure, if anything
   * @param raw the scrubbed device output
   * @param <T> the result type
   */
  record Failed<T extends CommandResult>(
      ServiceError error, Optional<T> partialResult, List<String> raw)
      implements CommandOutcome<T> {

    /** Takes an immutable copy of the raw output. */
    public Failed {
      raw = List.copyOf(raw);
    }
  }
}
