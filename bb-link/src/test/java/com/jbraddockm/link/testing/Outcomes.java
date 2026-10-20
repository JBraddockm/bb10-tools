package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;

/** Extracts the result of a successful {@link CommandOutcome} or the failure of a failed one. */
public final class Outcomes {

  private Outcomes() {}

  /**
   * The result of an outcome that should have succeeded.
   *
   * @param outcome the outcome
   * @param <T> what the command gives
   * @return the result
   * @throws AssertionError if the outcome is a failure
   */
  public static <T extends CommandResult> T succeeded(CommandOutcome<T> outcome) {
    if (outcome instanceof CommandOutcome.Succeeded<T> success) {
      return success.result();
    }
    throw new AssertionError("should have succeeded: " + outcome);
  }

  /**
   * An outcome that should have failed.
   *
   * @param outcome the outcome
   * @param <T> what the command gives
   * @return the failure
   * @throws AssertionError if the outcome is a success
   */
  public static <T extends CommandResult> CommandOutcome.Failed<T> failed(
      CommandOutcome<T> outcome) {
    if (outcome instanceof CommandOutcome.Failed<T> failure) {
      return failure;
    }
    throw new AssertionError("should have failed: " + outcome);
  }
}
