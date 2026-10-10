package com.jbraddockm.device;

import com.jbraddockm.DeployError;
import com.jbraddockm.ErrorCode;
import com.jbraddockm.output.CommandFailure;
import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.LogEntry;
import com.jbraddockm.output.LogLevel;
import com.jbraddockm.output.OutputParser;
import com.jbraddockm.output.Progress;
import java.util.List;
import java.util.Optional;

/**
 * Turns the captured output of one run into a {@link CommandOutcome}.
 *
 * <p>The live path and the offline replay both go through here, so a recorded run ends the way a
 * real one would. A parser that throws never turns a successful run into a failed one: the problem
 * is logged as a warning and the raw lines remain available.
 */
final class RunInterpreter {

  private RunInterpreter() {}

  /**
   * Reports one line while the command is still running: as progress if the parser says so, else as
   * a log entry.
   */
  static <T extends CommandResult> void forward(
      String line, OutputParser<T> parser, CommandListener listener) {
    Optional<Progress> progress = Optional.empty();
    try {
      progress = parser.progressOf(line);
    } catch (RuntimeException failure) {
      listener.onLog(LogLevel.WARN, "progress detection failed: " + failure);
    }
    if (progress.isPresent()) {
      listener.onProgress(progress.get());
      return;
    }
    LogEntry.fromOutputLine(line)
        .ifPresent(entry -> listener.onLog(entry.level(), entry.message()));
  }

  /** The tool exited normally: still a failure if the output says so, otherwise a success. */
  static <T extends CommandResult> CommandOutcome<T> completed(
      OutputParser<T> parser, List<String> lines, CommandListener listener) {
    var reported = detectFailure(parser, lines, listener);
    if (reported.isPresent()) {
      var failure = reported.get();
      return new CommandOutcome.Failed<>(
          new DeployError(ErrorCode.COMMAND_FAILED, failure.message(), failure.details()),
          Optional.empty(),
          List.copyOf(lines));
    }
    return new CommandOutcome.Succeeded<>(parse(parser, lines, listener), List.copyOf(lines));
  }

  /**
   * The tool raised an exception: classify it, and keep what the output still tells for parsers
   * that can.
   */
  static <T extends CommandResult> CommandOutcome<T> failed(
      OutputParser<T> parser, List<String> lines, DeployError error, CommandListener listener) {
    var partial =
        parser.parsesFailedRuns() && !lines.isEmpty()
            ? parse(parser, lines, listener)
            : Optional.<T>empty();
    return new CommandOutcome.Failed<>(error, partial, List.copyOf(lines));
  }

  /**
   * The first error line after the last non-failure verdict, which is where the real tool raises
   * its exception ("the last outcome wins", so a retried install that finally worked has none).
   */
  static Optional<String> toolErrorLine(List<String> lines) {
    String errorLine = null;
    for (var line : lines) {
      if (line.startsWith("Error:") || line.startsWith("result::failure")) {
        errorLine = errorLine == null ? line : errorLine;
      } else if (line.startsWith("result::")) {
        errorLine = null;
      }
    }
    return Optional.ofNullable(errorLine);
  }

  private static <T extends CommandResult> Optional<CommandFailure> detectFailure(
      OutputParser<T> parser, List<String> lines, CommandListener listener) {
    try {
      return parser.detectFailure(lines);
    } catch (RuntimeException failure) {
      listener.onLog(LogLevel.WARN, "failure detection failed, accepting the command: " + failure);
      return Optional.empty();
    }
  }

  private static <T extends CommandResult> Optional<T> parse(
      OutputParser<T> parser, List<String> lines, CommandListener listener) {
    try {
      return Optional.of(parser.parse(lines));
    } catch (RuntimeException failure) {
      listener.onLog(LogLevel.WARN, "parser failed, only the raw output is available: " + failure);
      return Optional.empty();
    }
  }
}
