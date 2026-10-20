package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.testing.DeviceFixture;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link DeviceSteps}: the steps shared by the pairing and certificate workflows. */
@DisplayName("DeviceSteps: the steps that the pairing and the certificate share")
class DeviceStepsTest {

  private DeviceFixture fixture;
  private DeviceSteps steps;

  @BeforeEach
  void openTheDevice() {
    fixture = DeviceFixture.withoutAuthority();
    steps = new DeviceSteps(fixture.session(), new Logs());
  }

  @AfterEach
  void closeTheDevice() {
    fixture.close();
  }

  @Nested
  @DisplayName("confirmPin")
  class ConfirmPin {

    @ParameterizedTest(name = "\"{0}\" is the PIN of the device")
    @ValueSource(strings = {"2A3B4C5D", "2a3b4c5d", "  2A3B4C5D  "})
    @DisplayName(
        "gives the PIN of the device in capitals when the PIN that was typed is the same, in any"
            + " case and with blanks")
    void givesThePinInCapitals(String typed) {
      assertThat(steps.confirmPin(typed)).isEqualTo("2A3B4C5D");
    }

    @Test
    @DisplayName("ends the workflow with PIN_MISMATCH when the device has another PIN")
    void endsWithAMismatch() {
      assertThatThrownBy(() -> steps.confirmPin("FFFFFFFF"))
          .isInstanceOfSatisfying(
              DeviceSteps.Stopped.class,
              stopped -> assertThat(stopped.error().code()).isEqualTo(LinkErrorCode.PIN_MISMATCH));
    }
  }

  @Nested
  @DisplayName("run")
  class Run {

    @Test
    @DisplayName("gives the result of a command that went well")
    void givesTheResult() {
      assertThat(steps.run(new GetPin()).pin()).isNotBlank();
    }

    @Test
    @DisplayName("ends the workflow with the error of a command that failed, as the device gave it")
    void endsWithTheErrorOfTheCommand() {
      var command =
          new GetAuthenticationCertificate(
              Resources.text(DeviceStepsTest.class, "device-steps--long-answer.txt"));

      assertThatThrownBy(() -> steps.run(command))
          .isInstanceOfSatisfying(
              DeviceSteps.Stopped.class,
              stopped -> {
                assertThat(stopped.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
                assertThat(stopped.getMessage()).isEqualTo(stopped.error().message());
              });
    }
  }

  @Nested
  @DisplayName("unexpected")
  class Unexpected {

    @Test
    @DisplayName(
        "is an UNEXPECTED_RESPONSE with what is missing, the status of the answer, and the answer")
    void saysWhatIsMissing() {
      var stopped = DeviceSteps.unexpected("no certificate", "Success", "the answer");

      assertThat(stopped.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(stopped.error().message()).isEqualTo("no certificate");
      assertThat(stopped.error().details())
          .containsEntry("status", "Success")
          .containsEntry("answer", "the answer");
    }

    @Test
    @DisplayName("gives an empty status when the answer had none")
    void givesAnEmptyStatus() {
      assertThat(DeviceSteps.unexpected("x", null, "y").error().details())
          .containsEntry("status", "");
    }

    @Test
    @DisplayName(
        "shortens an answer of more than 600 characters to 600 and a mark, and keeps a shorter one")
    void shortensALongAnswer() {
      var longAnswer =
          Resources.text(DeviceStepsTest.class, "device-steps--long-answer.txt").strip();
      var shortened =
          Resources.text(DeviceStepsTest.class, "device-steps--long-answer--shortened.txt").strip();

      assertThat(DeviceSteps.unexpected("x", "s", longAnswer).error().details())
          .containsEntry("answer", shortened);
      assertThat(DeviceSteps.unexpected("x", "s", shortened.substring(0, 600)).error().details())
          .containsEntry("answer", shortened.substring(0, 600));
    }
  }

  @Nested
  @DisplayName("Stopped")
  class Stopped {

    @Test
    @DisplayName("holds an error, and its message is the message of the error")
    void holdsAnError() {
      var error = new ServiceError(CoreErrorCode.IO_ERROR, "no space");

      var stopped = new DeviceSteps.Stopped(error);

      assertThat(stopped.error()).isSameAs(error);
      assertThat(stopped).hasMessage("no space").isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("can be made from a code, a message and details")
    void canBeMadeFromTheParts() {
      var stopped =
          new DeviceSteps.Stopped(CoreErrorCode.INTERNAL, "broken", Map.of("where", "here"));

      assertThat(stopped.error().code()).isEqualTo(CoreErrorCode.INTERNAL);
      assertThat(stopped.error().details()).containsEntry("where", "here");
    }
  }
}
