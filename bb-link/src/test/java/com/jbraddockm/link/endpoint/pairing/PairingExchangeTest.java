package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseStatus;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link PairingExchange}: turning the answer of {@code pairing.cgi} into the next step.
 */
@DisplayName("PairingExchange: the conversation with pairing.cgi")
class PairingExchangeTest {

  private static final CgiRequest REQUEST = CgiRequest.Form.to(Endpoint.PAIRING);

  private final PairingExchange exchange = new PairingExchange(REQUEST);

  private Step<PairingReply> answered(String resource) {
    var text = Resources.text(PairingExchangeTest.class, resource);
    return exchange.onAnswer(
        new CgiResponse(200, "text/xml", text, null), ResponseEnvelope.parse(text));
  }

  @Test
  @DisplayName("start sends the request it was made with")
  void startSendsTheRequest() {
    assertThat(exchange.start())
        .isInstanceOfSatisfying(
            Step.Send.class, send -> assertThat(send.request()).isSameAs(REQUEST));
  }

  @Test
  @DisplayName("an answer with the device's authority finishes with the reply")
  void aSuccessFinishes() {
    assertThat(answered("pairing-reply--authority-accepted.xml"))
        .isInstanceOfSatisfying(
            Step.Finish.class,
            finish -> assertThat(((PairingReply) finish.result()).deviceAuthority()).isPresent());
  }

  @Test
  @DisplayName("a success that has no authority still finishes: the caller decides what that is")
  void aSuccessWithoutAnAuthorityFinishes() {
    assertThat(answered("pairing-reply--success-without-authority.xml"))
        .isInstanceOf(Step.Finish.class);
  }

  @Test
  @DisplayName(
      "an error fails with the device's description and number, and the reply that was read")
  void anErrorFails() {
    assertThat(answered("pairing-reply--error.xml"))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
              assertThat(fail.error().message()).isEqualTo("Error - insert certificate failed.");
              assertThat(fail.error().details()).containsEntry("errorIdentifier", 7);
              assertThat(((Step.Fail<?>) fail).partial()).isPresent();
            });
  }

  @Test
  @DisplayName("an error whose description carries a secret is said without it")
  void anErrorWithASecretInItsDescription() {
    assertThat(answered("pairing-reply--error-with-cookie.xml"))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().message()).isEqualTo("Denied");
              assertThat(fail.error().details()).containsEntry("errorIdentifier", 7);
            });
  }

  @Test
  @DisplayName("an error with an empty description says that the device reported an error")
  void anErrorWithAnEmptyDescription() {
    assertThat(answered("pairing-reply--error-with-empty-description.xml"))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> assertThat(fail.error().message()).isEqualTo("The device reported an error"));
  }

  @Test
  @DisplayName(
      "an error with no description says that the device reported an error, and has no details")
  void anErrorWithoutADescription() {
    assertThat(answered("pairing-reply--error-without-description.xml"))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().message()).isEqualTo("The device reported an error");
              assertThat(fail.error().details()).isEmpty();
            });
  }

  @ParameterizedTest(name = "{0}: status \"{1}\"")
  @CsvSource({
    "pairing-reply--ready.xml, Ready",
    "pairing-reply--denied.xml, Denied",
    "pairing-reply--in-progress.xml, InProgress",
    "pairing-reply--strange.xml, Strange"
  })
  @DisplayName(
      "a status that is neither a success nor an error fails as unexpected, and gives the status")
  void anotherStatusFails(String resource, String status) {
    assertThat(answered(resource))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
              assertThat(fail.error().message()).contains("not a success or an error");
              assertThat(fail.error().details()).containsEntry("status", status);
              assertThat(((Step.Fail<?>) fail).partial()).isPresent();
            });
  }

  @Test
  @DisplayName("an answer with no status at all fails as unexpected, with an empty status")
  void noStatusFails() {
    assertThat(answered("pairing-reply--no-status-text-only.xml"))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> assertThat(fail.error().details()).containsEntry("status", ""));
  }

  @Test
  @DisplayName(
      "an answer that is not XML fails, says so, and keeps what it was as an unreadable reply")
  void anAnswerThatIsNotXmlFails() {
    var text = Resources.text(PairingExchangeTest.class, "pairing-reply--not-xml.txt");

    var step = exchange.onAnswer(new CgiResponse(502, "text/html", text, null), null);

    assertThat(step)
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
              assertThat(fail.error().message()).contains("HTTP 502");
              assertThat(
                      ((Optional<?>) fail.partial())
                          .map(PairingReply.class::cast)
                          .orElseThrow()
                          .status())
                  .isEqualTo(ResponseStatus.UNKNOWN);
            });
  }
}
