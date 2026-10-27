package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.testing.TestResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link Step}. */
@DisplayName("Step: the next move of a conversation")
class StepTest {

  private static final CgiRequest REQUEST = CgiRequest.Get.to(Endpoint.SUPPORT);

  @Nested
  @DisplayName("Send")
  class Send {

    @Test
    @DisplayName("with a request alone has no effects to apply before it")
    void hasNoEffectsByDefault() {
      var send = new Step.Send<TestResult>(REQUEST);

      assertThat(send.request()).isSameAs(REQUEST);
      assertThat(send.before()).isEmpty();
    }

    @Test
    @DisplayName("keeps the effects in order, as a copy that cannot be changed")
    void keepsACopyOfTheEffects() {
      var effects = new ArrayList<StepEffect>();
      effects.add(new StepEffect.Note(LogLevel.INFO, "one"));
      var send = new Step.Send<TestResult>(REQUEST, effects);

      effects.add(new StepEffect.Note(LogLevel.INFO, "two"));

      assertThat(send.before()).hasSize(1);
      assertThatThrownBy(() -> send.before().add(new StepEffect.Note(LogLevel.INFO, "three")))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("needs a request")
    void needsARequest() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Step.Send<TestResult>(null))
          .withMessage("request");
    }
  }

  @Nested
  @DisplayName("Fail")
  class Fail {

    private final ServiceError error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "no");

    @Test
    @DisplayName("with an error alone has no partial result")
    void hasNoPartialResultByDefault() {
      var fail = new Step.Fail<TestResult>(error);

      assertThat(fail.error()).isSameAs(error);
      assertThat(fail.partial()).isEmpty();
    }

    @Test
    @DisplayName("can carry what had been done when it failed")
    void canCarryAPartialResult() {
      var partial = new TestResult("half");

      assertThat(new Step.Fail<>(error, Optional.of(partial)).partial()).contains(partial);
    }
  }

  @Test
  @DisplayName("Finish holds the result")
  void finishHoldsTheResult() {
    var result = new TestResult("done");

    assertThat(new Step.Finish<>(result).result()).isSameAs(result);
  }

  @Test
  @DisplayName("a step is a send, a finish or a fail, and nothing else")
  void aStepIsOneOfThree() {
    List<Step<TestResult>> steps =
        List.of(
            new Step.Send<>(REQUEST),
            new Step.Finish<>(new TestResult("x")),
            new Step.Fail<>(new ServiceError(CoreErrorCode.INTERNAL, "x")));

    var kinds =
        steps.stream()
            .map(
                step ->
                    switch (step) {
                      case Step.Send<TestResult> _ -> "send";
                      case Step.Finish<TestResult> _ -> "finish";
                      case Step.Fail<TestResult> _ -> "fail";
                    })
            .toList();

    assertThat(kinds).containsExactly("send", "finish", "fail");
  }
}
