package com.jbraddockm.output;

import java.util.List;
import java.util.Optional;

/**
 * Interprets the output of one {@code bb-deploy} command.
 *
 * <p>An implementation is a pure function of text: it holds no state between calls and needs no
 * device. Lines are passed without line terminators.
 *
 * @param <T> the type of result the parser produces
 */
public interface OutputParser<T extends CommandResult> {

  /**
   * Builds the typed result from the complete output of a command.
   *
   * <p>Implementations must not throw for unexpected input. Lines they cannot interpret belong in
   * the result (typically as an {@code unparsed} list) so that nothing is silently lost.
   *
   * @param lines every line the tool printed for the command, in order
   * @return the result, never {@code null}
   */
  T parse(List<String> lines);

  /**
   * Reports a failure that the output reveals even though the tool exited normally.
   *
   * <p>Some failures are only visible in the text. For example, launching an app that is not
   * installed prints {@code result::Invalid} and exits with status 0. Failures the tool itself
   * raises, such as an {@code Error:} line, are not the concern of a parser.
   *
   * @param lines every line the tool printed for the command, in order
   * @return the failure, or empty if the output describes a success
   */
  default Optional<CommandFailure> detectFailure(List<String> lines) {
    return Optional.empty();
  }

  /**
   * Recognises a progress line while the command is still running.
   *
   * @param line one line of the tool's output
   * @return the progress it reports, or empty if the line is not a progress report
   */
  default Optional<Progress> progressOf(String line) {
    return Optional.empty();
  }

  /**
   * Tells whether {@link #parse} produces something meaningful for the output of a command that
   * failed.
   *
   * <p>An install that fails still prints the package identity and the sync decisions; callers can
   * use that partial result next to the failure.
   *
   * @return {@code true} if a result should be built even when the command failed
   */
  default boolean parsesFailedRuns() {
    return false;
  }
}
