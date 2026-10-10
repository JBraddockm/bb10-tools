package com.jbraddockm.output;

import java.util.List;
import java.util.Optional;

/**
 * Parses the commands that answer with a single {@code result::<value>} line: launch and terminate.
 *
 * <p>Observed, all with exit status 0: {@code result::36503658} for a launch that worked, {@code
 * result::Terminated} for a terminate (also when the app was already stopped), and {@code
 * result::Invalid} for either when the app is not installed. Only {@code Invalid} is treated as a
 * failure: a launch succeeds with a number, so "anything but {@code success}" would fail every real
 * launch.
 */
final class VerdictParser implements OutputParser<Verdict> {

  private final String action;

  VerdictParser(String action) {
    this.action = action;
  }

  @Override
  public Verdict parse(List<String> lines) {
    return new Verdict(OutputText.lastResult(lines).orElse(null));
  }

  @Override
  public Optional<CommandFailure> detectFailure(List<String> lines) {
    return OutputText.invalidResult(action, lines);
  }
}
