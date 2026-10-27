package com.jbraddockm.link.endpoint.actions;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ConfirmedByPin}: the PIN guard that runs before a command that changes the
 * device.
 */
@DisplayName("ConfirmedByPin: a command that waits for the confirmation of the PIN")
class ConfirmedByPinTest {

  private static final CgiRequest PROBE_REQUEST = CgiRequest.Get.to(Endpoint.SUPPORT);

  /** A command that sends one request and finishes with the first answer. It only reads. */
  private record Probe() implements DeviceCommand<TestResult> {

    @Override
    public Conversation<TestResult> conversation() {
      return new Conversation<>() {
        @Override
        public Step<TestResult> start() {
          return new Step.Send<>(PROBE_REQUEST);
        }

        @Override
        public Step<TestResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
          return new Step.Finish<>(new TestResult("guarded"));
        }
      };
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  private static CgiResponse response(String resource) {
    return new CgiResponse(
        200, "text/xml", Resources.text(ConfirmedByPinTest.class, resource), null);
  }

  private static Step<TestResult> answer(Conversation<TestResult> conversation, String resource) {
    var answer = response(resource);
    return conversation.onAnswer(answer, ResponseEnvelope.parse(answer.body()));
  }

  @Nested
  @DisplayName("what it is")
  class Being {

    @Test
    @DisplayName("is a command that changes the device, whatever the command that it guards does")
    void isNotReadOnly() {
      assertThat(new ConfirmedByPin<>(new Probe(), "2A3B4C5D").readOnly()).isFalse();
    }

    @Test
    @DisplayName("needs a command")
    void needsACommand() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ConfirmedByPin<TestResult>(null, "2A3B4C5D"))
          .withMessage("command");
    }

    @ParameterizedTest(name = "\"{0}\" is not a PIN")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("needs a PIN that is not blank, naming confirmPin")
    void needsAPin(String pin) {
      assertThatThrownBy(() -> new ConfirmedByPin<>(new Probe(), pin))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("confirmPin"));
    }

    @Test
    @DisplayName("begins by asking for the PIN, and not by the command")
    void beginsWithThePin() {
      assertThat(sent(new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation().start()))
          .isEqualTo(GetPin.request());
    }
  }

  @Nested
  @DisplayName("when the PIN is the one that was confirmed")
  class Matching {

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(
        strings = {
          "2A3B4C5D",
          "2a3b4c5d",
          "  2A3B4C5D  ",
          "0x2A3B4C5D",
          "0X2a3b4c5d",
          " 0x2a3b4c5d "
        })
    @DisplayName("the command begins, however the PIN was typed")
    void theCommandBegins(String typed) {
      var conversation = new ConfirmedByPin<>(new Probe(), typed).conversation();
      conversation.start();

      var step = answer(conversation, "pin--success.xml");

      assertThat(sent(step)).isEqualTo(PROBE_REQUEST);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pin--without-prefix.xml", "pin--with-blanks.xml"})
    @DisplayName("the command begins however the device wrote its PIN")
    void theDeviceMayWriteItsPinOtherwise(String resource) {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();

      assertThat(sent(answer(conversation, resource))).isEqualTo(PROBE_REQUEST);
    }

    @Test
    @DisplayName(
        "the answers that come after go to the command, which finishes with its own result")
    void laterAnswersGoToTheCommand() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();
      answer(conversation, "pin--success.xml");

      var step = answer(conversation, "pin--success.xml");

      assertThat(finished(step)).isEqualTo(new TestResult("guarded"));
    }
  }

  @Nested
  @DisplayName("when it is not")
  class NotMatching {

    @Test
    @DisplayName("the command does not begin: PIN_MISMATCH, with the words for it")
    void aPinThatIsNotTheDevices() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();

      var error = failed(answer(conversation, "pin--other-device.xml"));

      assertThat(error.code()).isEqualTo(LinkErrorCode.PIN_MISMATCH);
      assertThat(error.message())
          .isEqualTo("The confirmation PIN does not match the PIN of the device");
    }

    @Test
    @DisplayName(
        "the PIN that is typed is not found inside the PIN of the device: a part of it is not it")
    void aPartOfThePinIsNotThePin() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5").conversation();
      conversation.start();

      assertThat(failed(answer(conversation, "pin--success.xml")).code())
          .isEqualTo(LinkErrorCode.PIN_MISMATCH);
    }
  }

  @Nested
  @DisplayName("when the PIN cannot be read")
  class Unreadable {

    @Test
    @DisplayName("an answer that is not XML fails the conversation, with the words for that")
    void notXml() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();
      var text = response("pin--not-xml.txt");

      assertThat(failed(conversation.onAnswer(text, null)))
          .isEqualTo(ResponseFailures.notXml(text));
    }

    @Test
    @DisplayName("an error of the device fails it with that error, and the command does not begin")
    void anErrorOfTheDevice() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();

      assertThat(failed(answer(conversation, "pin--error.xml")).code())
          .isEqualTo(CoreErrorCode.COMMAND_FAILED);
    }

    @Test
    @DisplayName("an answer about something else fails it as not what was expected")
    void anotherElement() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();
      var envelope =
          ResponseEnvelope.parse(
              Resources.text(ConfirmedByPinTest.class, "pin--wrong-element.xml"));

      assertThat(failed(answer(conversation, "pin--wrong-element.xml")))
          .isEqualTo(ResponseFailures.notExpected(envelope, ResponseElement.DEVICE_PIN));
    }

    @Test
    @DisplayName("an answer with no PIN fails it as unexpected")
    void noPin() {
      var conversation = new ConfirmedByPin<>(new Probe(), "2A3B4C5D").conversation();
      conversation.start();

      assertThat(failed(answer(conversation, "pin--empty.xml")).code())
          .isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
    }
  }
}
