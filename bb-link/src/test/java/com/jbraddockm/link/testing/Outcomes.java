package com.jbraddockm.link.testing;

import static com.jbraddockm.link.testing.Checks.check;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;

/** What checks of workflows do with the outcome of a command. */
public final class Outcomes {

  private Outcomes() {}

  /**
   * The result of an outcome that should have succeeded.
   *
   * @param outcome the outcome
   * @param <T> what the command gives
   * @return the result; a check fails if there is none
   */
  public static <T extends CommandResult> T succeeded(CommandOutcome<T> outcome) {
    check(outcome instanceof CommandOutcome.Succeeded<T>, "should have succeeded: " + outcome);
    return ((CommandOutcome.Succeeded<T>) outcome).result();
  }

  /**
   * An outcome that should have failed.
   *
   * @param outcome the outcome
   * @param <T> what the command gives
   * @return the failure; a check fails if the outcome is a success
   */
  public static <T extends CommandResult> CommandOutcome.Failed<T> failed(
      CommandOutcome<T> outcome) {
    check(outcome instanceof CommandOutcome.Failed<T>, "should have failed: " + outcome);
    return (CommandOutcome.Failed<T>) outcome;
  }
}
