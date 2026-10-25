package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link AuthenticationExchange}: turning the answer of {@code login.cgi} into the next
 * step.
 */
@DisplayName("AuthenticationExchange: the conversation with login.cgi about a certificate")
class AuthenticationExchangeTest {

  private static final CgiRequest REQUEST =
      CgiRequest.Body.of(Endpoint.LOGIN, "x", "application/octet-stream");

  private final AuthenticationExchange exchange = new AuthenticationExchange(REQUEST);

  private Step<AuthenticationAnswer> answered(String resource, boolean xml) {
    var text = Resources.text(AuthenticationExchangeTest.class, resource);
    var envelope = xml ? ResponseEnvelope.parse(text) : null;
    return exchange.onAnswer(
        new CgiResponse(200, xml ? "text/xml" : "application/octet-stream", text, null), envelope);
  }

  @Test
  @DisplayName("start sends the request it was made with")
  void startSendsTheRequest() {
    assertThat(exchange.start())
        .isInstanceOfSatisfying(
            Step.Send.class, send -> assertThat(send.request()).isSameAs(REQUEST));
  }

  @Test
  @DisplayName("a certificate finishes with the answer")
  void aCertificateFinishes() {
    assertThat(answered("authentication-answer--certificate.pem", false))
        .isInstanceOfSatisfying(
            Step.Finish.class,
            finish ->
                assertThat(((AuthenticationAnswer) finish.result()).certificate()).isPresent());
  }

  @Test
  @DisplayName(
      "an answer with no certificate and no error still finishes: the caller decides what that is")
  void noCertificateStillFinishes() {
    assertThat(answered("authentication-answer--success-without-certificate.xml", true))
        .isInstanceOf(Step.Finish.class);
    assertThat(answered("authentication-answer--not-xml-not-pem.txt", false))
        .isInstanceOf(Step.Finish.class);
  }

  @Test
  @DisplayName(
      "the real error 260 fails with the device's description and number, and keeps the answer")
  void theRealError260Fails() {
    assertThat(answered("authentication-answer--content-type-error.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
              assertThat(fail.error().message())
                  .isEqualTo("Expected ContentType of application/octet-stream");
              assertThat(fail.error().details()).containsEntry("errorIdentifier", 260);
              assertThat(((Step.Fail<?>) fail).partial()).isPresent();
            });
  }

  @Test
  @DisplayName("takes the status Error in any case")
  void takesTheStatusInAnyCase() {
    assertThat(answered("authentication-answer--lowercase-error.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class, fail -> assertThat(fail.error().message()).isEqualTo("Lower"));
  }

  @Test
  @DisplayName("an error whose description carries a secret is said without it")
  void anErrorWithASecretInItsDescription() {
    assertThat(answered("authentication-answer--error-with-cookie.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().message()).isEqualTo("Denied");
              assertThat(fail.error().details()).containsEntry("errorIdentifier", 260);
            });
  }

  @Test
  @DisplayName("an error with an empty description says that the device reported an error")
  void anErrorWithAnEmptyDescription() {
    assertThat(answered("authentication-answer--error-with-empty-description.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> assertThat(fail.error().message()).isEqualTo("The device reported an error"));
  }

  @Test
  @DisplayName(
      "an error with no description says that the device reported an error, and has no details")
  void anErrorWithoutADescription() {
    assertThat(answered("authentication-answer--error-without-description.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().message()).isEqualTo("The device reported an error");
              assertThat(fail.error().details()).isEmpty();
            });
  }

  @Test
  @DisplayName("an error whose number is not a number has no number in its details")
  void anErrorWhoseNumberIsNotANumber() {
    assertThat(answered("authentication-answer--error-identifier-not-a-number.xml", true))
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> assertThat(fail.error().details()).doesNotContainKey("errorIdentifier"));
  }
}
