package com.jbraddockm.link.actions;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link GetPin}. */
@DisplayName("GetPin: reading the PIN of the device")
class GetPinTest {

  private static CgiResponse response(String resource) {
    return new CgiResponse(200, "text/xml", Resources.text(GetPinTest.class, resource), null);
  }

  @Test
  @DisplayName("asks wipe.cgi for wipe=getpin, and says that it is reading the PIN")
  void asksForThePin() {
    var request = GetPin.request();

    assertThat(request.endpoint()).isEqualTo(Endpoint.WIPE);
    assertThat(request.encodedBody()).isEqualTo("wipe=getpin");
    assertThat(request.describe()).isEqualTo("Reading the device PIN");
  }

  @Test
  @DisplayName("begins by sending that request, and nothing else")
  void beginsWithTheRequest() {
    assertThat(sent(new GetPin().conversation().start())).isEqualTo(GetPin.request());
  }

  @Test
  @DisplayName("finishes with the PIN that the device answered")
  void finishesWithThePin() {
    var answer = response("pin--success.xml");

    var step = new GetPin().conversation().onAnswer(answer, ResponseEnvelope.parse(answer.body()));

    assertThat(finished(step)).isEqualTo(new DevicePin("2A3B4C5D"));
  }

  @Test
  @DisplayName("fails when the answer is not XML, with the words for that")
  void failsForAnAnswerThatIsNotXml() {
    var answer = response("pin--not-xml.txt");

    var step = new GetPin().conversation().onAnswer(answer, null);

    assertThat(failed(step)).isEqualTo(ResponseFailures.notXml(answer));
  }

  @Test
  @DisplayName("only reads: it changes nothing on the device")
  void onlyReads() {
    assertThat(new GetPin().readOnly()).isTrue();
  }

  @Test
  @DisplayName("gives its result as the PIN, in the pin field")
  void givesItsResultAsJson() {
    assertThat(new DevicePin("2A3B4C5D").toJson())
        .containsOnlyKeys("pin")
        .containsEntry("pin", "2A3B4C5D");
  }
}
