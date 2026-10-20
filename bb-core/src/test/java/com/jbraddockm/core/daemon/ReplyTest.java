package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import com.jbraddockm.core.testing.TestPhase;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Reply}: the events of a request, which always ends with exactly one terminal
 * event.
 */
@DisplayName("Reply: the events of one request")
class ReplyTest {

  private final ByteArrayOutputStream written = new ByteArrayOutputStream();
  private final Reply reply =
      Reply.standalone(new PrintStream(written, true, StandardCharsets.UTF_8), 7);

  private List<String> lines() {
    return written.toString(StandardCharsets.UTF_8).lines().toList();
  }

  private static String expected(String resource) {
    return Resources.text(ReplyTest.class, resource).strip();
  }

  @Nested
  @DisplayName("what it says while it runs")
  class Running {

    @Test
    @DisplayName("a log is an event with its level in small letters and its message")
    void aLog() {
      reply.onLog(LogLevel.WARN, "careful");

      assertThat(lines()).containsExactly(expected("reply--log.json"));
    }

    @Test
    @DisplayName(
        "a progress is an event that has the fields of the progress: the percent, the phase, the"
            + " bytes and the item")
    void aProgress() {
      reply.onProgress(new Progress(40, TestPhase.UPLOADING, 512L, 1024L, "app.bar"));

      assertThat(lines()).containsExactly(expected("reply--progress.json"));
    }

    @Test
    @DisplayName(
        "an event of the tool's own has the type that it is given and its fields, after the"
            + " identifier and the type")
    void anEventOfTheTool() {
      reply.event("device", Json.object("hostname", "h", "address", "a"));

      assertThat(lines()).containsExactly(expected("reply--event.json"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"log", "progress", "result", "done", "hello"})
    @DisplayName("an event cannot have a type that the protocol keeps for itself")
    void reservedTypes(String type) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> reply.event(type, Json.object()))
          .withMessage("reserved event type or field");
      assertThat(lines()).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"id", "type"})
    @DisplayName("an event cannot have a field that would replace the identifier or the type")
    void reservedFields(String field) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> reply.event("device", Json.object(field, "x")))
          .withMessage("reserved event type or field");
      assertThat(lines()).isEmpty();
    }
  }

  @Nested
  @DisplayName("the results and the end")
  class Ending {

    @Test
    @DisplayName(
        "a result has the command, the data and the raw answers, and no raw answers when there are"
            + " none to give")
    void aResult() {
      reply.result("getPin", Json.object("pin", "2A3B4C5D"), List.of("<a/>"));
      reply.result("getPin", Json.object("pin", "2A3B4C5D"), null);

      assertThat(lines())
          .containsExactly(
              expected("reply--result.json"), expected("reply--result-without-raw.json"));
    }

    @Test
    @DisplayName(
        "a success ends the request with ok true, once, however often it is said, and is not undone"
            + " by a failure after it")
    void aSuccessIsWrittenOnce() {
      reply.succeed();
      reply.succeed();
      reply.fail(new ServiceError(CoreErrorCode.INTERNAL, "late"));

      assertThat(lines()).containsExactly(expected("reply--done.json"));
      assertThat(reply.isFinished()).isTrue();
    }

    @Test
    @DisplayName(
        "a failure ends the request with ok false and the error, once, and is not undone by a"
            + " success after it")
    void aFailureIsWrittenOnce() {
      reply.fail(new ServiceError(CoreErrorCode.COMMAND_FAILED, "no", Json.object("code", 1013)));
      reply.fail(new ServiceError(CoreErrorCode.INTERNAL, "again"));
      reply.succeed();

      assertThat(lines()).containsExactly(expected("reply--failed.json"));
    }

    @Test
    @DisplayName("is not finished until it has been ended")
    void notFinishedAtFirst() {
      assertThat(reply.isFinished()).isFalse();
      reply.onLog(LogLevel.INFO, "still going");
      assertThat(reply.isFinished()).isFalse();
    }

    @Test
    @DisplayName(
        "complete writes the result and then the success, with the raw answers when they are asked"
            + " for")
    void completeASuccess() {
      var outcome = new CommandOutcome.Succeeded<>(NoData.INSTANCE, List.of("<a/>"));

      reply.complete("x", outcome, true);

      assertThat(lines())
          .containsExactly(expected("reply--complete-with-raw.json"), expected("reply--done.json"));
    }

    @Test
    @DisplayName("complete leaves out the raw answers when they are not asked for")
    void completeWithoutRaw() {
      reply.complete("x", new CommandOutcome.Succeeded<>(NoData.INSTANCE, List.of("<a/>")), false);

      assertThat(lines())
          .containsExactly(
              expected("reply--complete-without-raw.json"), expected("reply--done.json"));
    }

    @Test
    @DisplayName(
        "complete of a failure writes the partial result first, if there is one, and then the"
            + " failure")
    void completeAFailure() {
      var error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "no", Json.object("code", 1013));

      reply.complete(
          "x",
          new CommandOutcome.Failed<>(error, Optional.of(NoData.INSTANCE), List.of("<a/>")),
          true);

      assertThat(lines())
          .containsExactly(
              expected("reply--complete-with-raw.json"), expected("reply--failed.json"));
    }

    @Test
    @DisplayName("complete of a failure with nothing partial writes only the failure")
    void completeAFailureWithNothingPartial() {
      var error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "no", Json.object("code", 1013));

      reply.complete("x", CommandOutcome.<NoData>failed(error), true);

      assertThat(lines()).containsExactly(expected("reply--failed.json"));
    }
  }

  @Nested
  @DisplayName("the identifier")
  class Identifier {

    @Test
    @DisplayName("of a request that could not be read is null in its events")
    void aNullId() {
      var output = new ByteArrayOutputStream();
      var unknown =
          Reply.to(new EventWriter(new PrintStream(output, true, StandardCharsets.UTF_8)), null);

      unknown.onLog(LogLevel.INFO, "hello");

      assertThat(output.toString(StandardCharsets.UTF_8).strip())
          .isEqualTo(expected("reply--null-id.json"));
    }

    @Test
    @DisplayName("of a request that was given as a text is echoed as a text")
    void aStringId() {
      var output = new ByteArrayOutputStream();
      var text =
          Reply.to(
              new EventWriter(new PrintStream(output, true, StandardCharsets.UTF_8)),
              new RequestId.Text("abc"));

      text.succeed();

      assertThat(output.toString(StandardCharsets.UTF_8).strip())
          .isEqualTo(expected("reply--string-id.json"));
    }
  }
}
