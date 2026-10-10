package com.jbraddockm.output;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses {@code -isAppRunning}. Observed, all with exit status 0: {@code result::true} for a
 * running app and {@code result::false} for a stopped app and for an app that is not installed. Any
 * other answer, or none, means the state is unknown, which is reported as a failure instead of
 * being guessed.
 */
final class RunStateParser implements OutputParser<AppRunState> {

  private static final String UNKNOWN_STATE = "Could not tell whether the app is running: ";

  private static Optional<Boolean> running(List<String> lines) {
    return OutputText.lastResult(lines)
        .flatMap(
            answer ->
                switch (answer.toLowerCase(Locale.ROOT)) {
                  case "true" -> Optional.of(Boolean.TRUE);
                  case "false" -> Optional.of(Boolean.FALSE);
                  default -> Optional.empty();
                });
  }

  @Override
  public AppRunState parse(List<String> lines) {
    return new AppRunState(running(lines).orElse(null));
  }

  @Override
  public Optional<CommandFailure> detectFailure(List<String> lines) {
    if (running(lines).isPresent()) {
      return Optional.empty();
    }
    return Optional.of(
        OutputText.lastResult(lines)
            .map(
                answer ->
                    new CommandFailure(
                        UNKNOWN_STATE + "the device reported '" + answer + "'",
                        Map.of("result", answer)))
            .orElseGet(
                () -> new CommandFailure(UNKNOWN_STATE + "the device sent no result", Map.of())));
  }
}
