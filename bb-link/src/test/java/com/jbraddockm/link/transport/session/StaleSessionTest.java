package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link StaleSession}: recognizing that the device no longer knows the session. */
@DisplayName("StaleSession: what tells that the device has lost the session")
class StaleSessionTest {

  private static final CgiRequest ASKING = CgiRequest.Get.to(Endpoint.DYNAMIC_PROPERTIES);
  private static final CgiRequest EXPECTING_NOTHING =
      CgiRequest.Get.to(Endpoint.PIM_SYNC).expectingNoBody();

  private static boolean indicated(CgiRequest request, int status, String resource) {
    var text = resource.isEmpty() ? "" : Resources.text(StaleSessionTest.class, resource);
    var envelope = text.strip().startsWith("<") ? ResponseEnvelope.parse(text) : null;
    return StaleSession.indicatedBy(
        request, new CgiResponse(status, "text/xml", text, null), envelope);
  }

  @Nested
  @DisplayName("the status of the answer")
  class Status {

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {401, 403})
    @DisplayName("401 and 403 mean that the session is lost, whatever the body is")
    void refusalMeansLost(int status) {
      assertThat(indicated(ASKING, status, "stale-session--properties.xml")).isTrue();
    }

    @ParameterizedTest(name = "HTTP {0}")
    @ValueSource(ints = {200, 404, 500, 502})
    @DisplayName("other statuses mean nothing by themselves")
    void otherStatusesMeanNothing(int status) {
      assertThat(indicated(ASKING, status, "stale-session--properties.xml")).isFalse();
    }
  }

  @Nested
  @DisplayName("an empty answer")
  class Empty {

    @Test
    @DisplayName("means that the session is lost, when the request did not expect an empty answer")
    void anUnexpectedEmptyAnswer() {
      assertThat(indicated(ASKING, 200, "")).isTrue();
    }

    @Test
    @DisplayName("means nothing when the request said that an empty answer is normal")
    void anExpectedEmptyAnswer() {
      assertThat(indicated(EXPECTING_NOTHING, 200, "")).isFalse();
    }

    @Test
    @DisplayName(
        "means nothing for a status other than 200, since it is the status that says something"
            + " then")
    void anEmptyAnswerWithAnotherStatus() {
      assertThat(indicated(ASKING, 500, "")).isFalse();
    }
  }

  @Nested
  @DisplayName("the words of the device")
  class Words {

    @Test
    @DisplayName("DTM NOT LOGGED IN, anywhere in the body, means that the session is lost")
    void notLoggedIn() {
      assertThat(indicated(ASKING, 200, "stale-session--not-logged-in.txt")).isTrue();
    }

    @Test
    @DisplayName("other text means nothing")
    void otherText() {
      assertThat(indicated(ASKING, 200, "stale-session--other-text.txt")).isFalse();
    }
  }

  @Nested
  @DisplayName("an error of authorization")
  class Authorization {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "stale-session--error-authorization.xml",
          "stale-session--error-authorization-with-dot.xml",
          "stale-session--error-authorization-capitals.xml"
        })
    @DisplayName(
        "means that the session is lost, with or without the full stop, in any case, with blanks"
            + " around")
    void anErrorOfAuthorization(String resource) {
      assertThat(indicated(ASKING, 200, resource)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "stale-session--error-other.xml",
          "stale-session--error-authorization-and-more.xml"
        })
    @DisplayName("is not what another error is, nor one that only starts with the same words")
    void anotherError(String resource) {
      assertThat(indicated(ASKING, 200, resource)).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
      "Error - authorization, true",
      "Error - authorization., true",
      "'  error - AUTHORIZATION .  ', true",
      "Error - authorization!, false",
      "Error - authorisation, false",
      "Error - authorization failed, false",
      "Error - something went wrong., false",
      "authorization, false",
      "Error, false",
      "'', false"
    })
    @DisplayName(
        "isNotAuthorized takes the description without blanks and a full stop, in any case")
    void isNotAuthorized(String description, boolean expected) {
      assertThat(StaleSession.isNotAuthorized(description)).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("the answer of a login")
  class Login {

    @Test
    @DisplayName("AuthChallenge means that the session is lost: the device asks to log in again")
    void anAuthChallenge() {
      assertThat(indicated(ASKING, 200, "stale-session--auth-challenge.xml")).isTrue();
    }

    @Test
    @DisplayName("Auth means that the session is lost too, as far as this check goes")
    void anAuth() {
      assertThat(indicated(ASKING, 200, "stale-session--auth.xml")).isTrue();
    }

    @Test
    @DisplayName("an ordinary answer means nothing")
    void anOrdinaryAnswer() {
      assertThat(indicated(ASKING, 200, "stale-session--properties.xml")).isFalse();
    }
  }
}
