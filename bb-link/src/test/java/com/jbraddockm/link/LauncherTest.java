package com.jbraddockm.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.Launcher.Invocation;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Launcher#interpret(String[])}: the command lines that start the daemon or a
 * replay, and those that print usage.
 */
@DisplayName("Launcher: what the command line asks for")
class LauncherTest {

  private static String usage(String resource) {
    return Resources.text(LauncherTest.class, resource).strip();
  }

  @Nested
  @DisplayName("the daemon")
  class Daemon {

    @Test
    @DisplayName("with no more is the daemon with no trace")
    void withNoTrace() {
      assertThat(Launcher.interpret(new String[] {"-daemon"}))
          .isEqualTo(new Invocation.Daemon(Optional.empty()));
    }

    @Test
    @DisplayName("with -trace and a folder is the daemon that records the exchanges in that folder")
    void withATrace() {
      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace", "traces/today"}))
          .isEqualTo(new Invocation.Daemon(Optional.of(Path.of("traces/today"))));
    }

    @Test
    @DisplayName(
        "with -trace and no folder, with something else, or with more, is not understood, and says"
            + " how the daemon is used")
    void notUnderstood() {
      var expected = new Invocation.Usage(usage("launcher--daemon-usage.txt"));

      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "-other"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "x", "y"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-daemon", "-trace", "folder", "extra"}))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("the replay")
  class Replay {

    @Test
    @DisplayName("-parse and a command is the replay of that command")
    void aCommand() {
      assertThat(Launcher.interpret(new String[] {"-parse", "getPin"}))
          .isEqualTo(new Invocation.Replay("getPin"));
    }

    @Test
    @DisplayName("-parse with no command, or with more than one, says how the tool is used")
    void notUnderstood() {
      var expected = new Invocation.Usage(usage("launcher--usage.txt"));

      assertThat(Launcher.interpret(new String[] {"-parse"})).isEqualTo(expected);
      assertThat(Launcher.interpret(new String[] {"-parse", "getPin", "extra"}))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("anything else")
  class Anything {

    @Test
    @DisplayName("no arguments says how the tool is used")
    void noArguments() {
      assertThat(Launcher.interpret(new String[] {}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(
        strings = {"daemon", "--daemon", "-DAEMON", "-Daemon", "parse", "-Parse", "-help", ""})
    @DisplayName("a first argument that is not exactly -daemon or -parse says how the tool is used")
    void aFirstArgumentThatIsNeither(String first) {
      assertThat(Launcher.interpret(new String[] {first}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
      assertThat(Launcher.interpret(new String[] {first, "x"}))
          .isEqualTo(new Invocation.Usage(usage("launcher--usage.txt")));
    }
  }
}
