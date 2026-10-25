package com.jbraddockm.link.properties;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GetDeviceProperties}: the request sent, and the result for each kind of answer.
 */
@DisplayName("GetDeviceProperties: reading the properties of the device")
class GetDevicePropertiesTest {

  private static ResponseEnvelope envelope(String resource) {
    return ResponseEnvelope.parse(Resources.text(GetDevicePropertiesTest.class, resource));
  }

  @Nested
  @DisplayName("the request")
  class Request {

    @Test
    @DisplayName(
        "is to dynamicProperties.cgi, with the field that asks for the properties, and says what it"
            + " does")
    void isTheRequestOfTheDevice() {
      var request = GetDeviceProperties.request();

      assertThat(request.endpoint()).isEqualTo(Endpoint.DYNAMIC_PROPERTIES);
      assertThat(request.encodedBody())
          .isEqualTo("Get%20Dynamic%20Properties=Get%20Dynamic%20Properties");
      assertThat(request.describe()).isEqualTo("Reading the device properties");
    }

    @Test
    @DisplayName("is what the conversation begins with, and the command only reads")
    void theConversationBeginsWithIt() {
      assertThat(sent(new GetDeviceProperties().conversation().start()))
          .isEqualTo(GetDeviceProperties.request());
      assertThat(new GetDeviceProperties().readOnly()).isTrue();
    }
  }

  @Nested
  @DisplayName("interpret")
  class Interpreting {

    @Test
    @DisplayName("finishes with the properties that the device answered")
    void finishesWithTheProperties() {
      assertThat(
              finished(GetDeviceProperties.interpret(envelope("properties--full.xml")))
                  .deviceName())
          .isEqualTo("BLACKBERRY-4C5D");
    }

    @Test
    @DisplayName("fails with the error that the device reported, with its words and its number")
    void failsWithTheErrorOfTheDevice() {
      var error = failed(GetDeviceProperties.interpret(envelope("properties--error.xml")));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo("Error - something failed");
      assertThat(error.details()).containsEntry("errorIdentifier", 1011);
    }

    @Test
    @DisplayName("fails when the answer is about something else than the properties")
    void failsForAnotherElement() {
      var other = envelope("properties--wrong-element.xml");

      assertThat(failed(GetDeviceProperties.interpret(other)))
          .isEqualTo(ResponseFailures.notExpected(other, ResponseElement.DYNAMIC_PROPERTIES));
    }

    @Test
    @DisplayName("fails as unexpected when the properties have nothing in them")
    void failsForEmptyProperties() {
      var error = failed(GetDeviceProperties.interpret(envelope("properties--empty.xml")));

      assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(error.message()).isEqualTo("The device returned empty properties");
    }
  }

  @Test
  @DisplayName("fails with the words for an answer that is not XML")
  void failsForAnAnswerThatIsNotXml() {
    var answer =
        new CgiResponse(
            200,
            "text/html",
            Resources.text(GetDevicePropertiesTest.class, "properties--not-xml.txt"),
            null);

    var step = new GetDeviceProperties().conversation().onAnswer(answer, null);

    assertThat(failed(step)).isEqualTo(ResponseFailures.notXml(answer));
  }
}
