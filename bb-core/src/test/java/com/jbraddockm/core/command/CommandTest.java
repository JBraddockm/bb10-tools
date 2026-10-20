package com.jbraddockm.core.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import com.jbraddockm.core.testing.TestPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the command model: {@link CommandOutcome}, {@link Progress}, {@link LogLevel}, {@link
 * NoData} and {@link CommandListener}.
 */
@DisplayName("Commands: outcomes, progress, levels and the silent listener")
class CommandTest {

  @Nested
  @DisplayName("CommandOutcome")
  class Outcomes {

    private final ServiceError error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "no");

    @Test
    @DisplayName(
        "succeeded is a result with no raw answers, and failed an error with no partial result and"
            + " no raw answers")
    void theTwoWaysToMakeOne() {
      var success = CommandOutcome.succeeded(NoData.INSTANCE);
      var failure = CommandOutcome.<NoData>failed(error);

      assertThat(success).isEqualTo(new CommandOutcome.Succeeded<>(NoData.INSTANCE, List.of()));
      assertThat(success.raw()).isEmpty();
      assertThat(failure)
          .isEqualTo(new CommandOutcome.Failed<>(error, Optional.empty(), List.of()));
      assertThat(failure.raw()).isEmpty();
    }

    @Test
    @DisplayName("keep their own copy of the raw answers, which nobody can change")
    void keepTheirOwnCopyOfTheRawAnswers() {
      var raw = new ArrayList<>(List.of("<a/>"));
      var success = new CommandOutcome.Succeeded<>(NoData.INSTANCE, raw);
      var failure = new CommandOutcome.Failed<NoData>(error, Optional.of(NoData.INSTANCE), raw);

      raw.add("<b/>");

      assertThat(success.raw()).containsExactly("<a/>");
      assertThat(failure.raw()).containsExactly("<a/>");
      assertThatThrownBy(() -> success.raw().add("x"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> failure.raw().add("x"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("a failure can carry what was got before it failed")
    void aFailureCanCarryAPartialResult() {
      var failure =
          new CommandOutcome.Failed<NoData>(error, Optional.of(NoData.INSTANCE), List.of());

      assertThat(failure.partialResult()).contains(NoData.INSTANCE);
      assertThat(failure.error()).isSameAs(error);
    }
  }

  @Nested
  @DisplayName("Progress")
  class Progresses {

    @ParameterizedTest(name = "{0} percent")
    @ValueSource(ints = {0, 1, 50, 99, 100})
    @DisplayName("takes any percent from 0 to 100")
    void takesAnyPercentFromZeroToOneHundred(int percent) {
      assertThat(Progress.ofPercent(percent, TestPhase.UPLOADING).percent()).isEqualTo(percent);
    }

    @ParameterizedTest(name = "{0} percent")
    @ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
    @DisplayName("refuses a percent that is below 0 or above 100, saying which")
    void refusesAPercentOutOfRange(int percent) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Progress.ofPercent(percent, TestPhase.UPLOADING))
          .withMessage("percent out of range: " + percent);
    }

    @Test
    @DisplayName("needs a phase")
    void needsAPhase() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Progress(10, null, null, null, null))
          .withMessage("phase");
    }

    @Test
    @DisplayName("ofPercent has no bytes and no item")
    void ofPercentHasNoBytesAndNoItem() {
      var progress = Progress.ofPercent(40, TestPhase.UPLOADING);

      assertThat(progress.bytesDone()).isNull();
      assertThat(progress.bytesTotal()).isNull();
      assertThat(progress.item()).isNull();
    }

    @Test
    @DisplayName(
        "is written with its percent and its phase, and with the bytes and the item when it has"
            + " them, and not otherwise")
    void isWrittenAsJson() {
      var minimal = Progress.ofPercent(40, TestPhase.UPLOADING);
      var full = new Progress(50, TestPhase.UPLOADING, 512L, 1024L, "app.bar");

      assertThat(List.copyOf(full.toJson().keySet()))
          .containsExactly("percent", "phase", "bytesDone", "bytesTotal", "item");
      assertThat(Json.write(minimal.toJson()))
          .isEqualTo(Resources.text(CommandTest.class, "progress--minimal.json").strip());
      assertThat(Json.write(full.toJson()))
          .isEqualTo(Resources.text(CommandTest.class, "progress--full.json").strip());
    }
  }

  @Nested
  @DisplayName("LogLevel, NoData and the silent listener")
  class Small {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"DEBUG, debug", "INFO, info", "WARN, warn"})
    @DisplayName("each level is written in small letters")
    void eachLevelIsWrittenInSmallLetters(LogLevel level, String wireName) {
      assertThat(level.wireName()).isEqualTo(wireName);
    }

    @Test
    @DisplayName("are three, from the least serious to the most")
    void areThree() {
      assertThat(LogLevel.values()).containsExactly(LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN);
    }

    @Test
    @DisplayName(
        "NoData is a result with no data: an empty object, a new one each time, which can be filled"
            + " without harm to the next")
    void noDataIsAnEmptyObject() {
      var first = NoData.INSTANCE.toJson();
      first.put("changed", 1);

      assertThat(NoData.INSTANCE.toJson()).isEmpty();
      assertThat(NoData.values()).containsExactly(NoData.INSTANCE);
    }

    @Test
    @DisplayName("the silent listener hears everything and does nothing, whatever it is told")
    void theSilentListenerDoesNothing() {
      CommandListener.SILENT.onLog(LogLevel.WARN, "ignored");
      CommandListener.SILENT.onProgress(Progress.ofPercent(10, TestPhase.UPLOADING));

      assertThat(CommandListener.SILENT).isSameAs(CommandListener.SILENT);
    }
  }
}
