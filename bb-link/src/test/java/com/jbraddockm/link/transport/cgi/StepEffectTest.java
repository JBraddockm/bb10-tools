package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.link.testing.TestPhase;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link StepEffect}. */
@DisplayName("StepEffect: what happens before the next request goes out")
class StepEffectTest {

  @Nested
  @DisplayName("Pause")
  class Pause {

    @Test
    @DisplayName("holds a time to wait, which may be nothing")
    void holdsATime() {
      assertThat(new StepEffect.Pause(Duration.ofSeconds(2)).duration())
          .isEqualTo(Duration.ofSeconds(2));
      assertThat(new StepEffect.Pause(Duration.ZERO).duration()).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("cannot be negative")
    void cannotBeNegative() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> new StepEffect.Pause(Duration.ofMillis(-1)))
          .withMessage("a pause cannot be negative");
    }

    @Test
    @DisplayName("needs a time")
    void needsATime() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StepEffect.Pause(null))
          .withMessage("duration");
    }
  }

  @Nested
  @DisplayName("Note")
  class Note {

    @Test
    @DisplayName("holds a level and a message")
    void holdsALevelAndAMessage() {
      var note = new StepEffect.Note(LogLevel.WARN, "careful");

      assertThat(note.level()).isEqualTo(LogLevel.WARN);
      assertThat(note.message()).isEqualTo("careful");
    }

    @Test
    @DisplayName("needs both")
    void needsBoth() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StepEffect.Note(null, "x"))
          .withMessage("level");
      assertThatNullPointerException()
          .isThrownBy(() -> new StepEffect.Note(LogLevel.INFO, null))
          .withMessage("message");
    }
  }

  @Nested
  @DisplayName("Report")
  class Report {

    @Test
    @DisplayName("holds the progress to report")
    void holdsTheProgress() {
      var progress = new Progress(50, TestPhase.UPLOADING, null, null, "package.bar");

      assertThat(new StepEffect.Report(progress).progress()).isSameAs(progress);
    }

    @Test
    @DisplayName("needs a progress")
    void needsAProgress() {
      assertThatNullPointerException()
          .isThrownBy(() -> new StepEffect.Report(null))
          .withMessage("progress");
    }
  }
}
