package com.jbraddockm.device;

import com.jbraddockm.DeployError;
import com.jbraddockm.output.CommandResult;
import java.util.List;
import java.util.Optional;

/**
 * How a command ended. Exactly one of the two variants applies, so callers can handle both with a
 * pattern-matching {@code switch}.
 *
 * @param <T> the type of result the command produces
 */
public sealed interface CommandOutcome<T extends CommandResult> {

  /**
   * Returns the lines the tool printed, exactly as printed apart from line terminators. They are
   * the fallback for anything the typed result does not capture.
   *
   * @return the output lines of the final attempt
   */
  List<String> rawLines();

  /**
   * The command worked.
   *
   * @param result the typed result; empty only if the parser itself failed, in which case {@code
   *     rawLines} is all there is
   * @param rawLines the output lines
   * @param <T> the type of result the command produces
   */
  record Succeeded<T extends CommandResult>(Optional<T> result, List<String> rawLines)
      implements CommandOutcome<T> {}

  /**
   * The command failed.
   *
   * @param error what went wrong
   * @param partialResult what could still be read from the output, for commands whose output stays
   *     meaningful after a failure (an install reports its sync decisions)
   * @param rawLines the output lines
   * @param <T> the type of result the command produces
   */
  record Failed<T extends CommandResult>(
      DeployError error, Optional<T> partialResult, List<String> rawLines)
      implements CommandOutcome<T> {}
}
