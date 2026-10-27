package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.link.testing.Resources;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CgiRequest.Body}. */
@DisplayName("CgiRequest.Body: a text sent as the body of a POST")
class CgiRequestBodyTest {

  private static final String CONTENT_TYPE = "application/octet-stream";

  private String text;
  private CgiRequest.Body base;

  @BeforeEach
  void makeTheBody() {
    text = Resources.text(CgiRequestBodyTest.class, "cgi-request--dummy-request.txt");
    base = CgiRequest.Body.of(Endpoint.LOGIN, text, CONTENT_TYPE);
  }

  @Test
  @DisplayName(
      "of makes a body with its text and content type, no query, no purpose and the usual time")
  void ofMakesTheBody() {
    assertThat(base.endpoint()).isEqualTo(Endpoint.LOGIN);
    assertThat(base.text()).isEqualTo(text);
    assertThat(base.contentType()).isEqualTo(CONTENT_TYPE);
    assertThat(base.query()).isEmpty();
    assertThat(base.purpose()).isEmpty();
    assertThat(base.readTimeout()).isEqualTo(CgiRequest.DEFAULT_READ_TIMEOUT);
  }

  @Test
  @DisplayName("an empty answer to a body is never normal")
  void anEmptyAnswerIsNeverNormal() {
    assertThat(base.emptyAnswerIsNormal()).isFalse();
  }

  @Test
  @DisplayName(
      "with adds a parameter, and pathAndQuery writes the parameters in order after the path")
  void withAddsParameters() {
    var body = base.with(QueryParameter.REQUEST_VERSION, "3").with(QueryParameter.CGI_AUTH, "1");

    assertThat(body.query())
        .containsExactly(
            Map.entry(QueryParameter.REQUEST_VERSION, "3"),
            Map.entry(QueryParameter.CGI_AUTH, "1"));
    assertThat(body.pathAndQuery()).isEqualTo("/cgi-bin/login.cgi?request_version=3&cgiauth=1");
    assertThat(base.pathAndQuery()).isEqualTo("/cgi-bin/login.cgi");
  }

  @Test
  @DisplayName(
      "describe says what is posted where when there is no purpose, and the purpose when there is")
  void describeSaysWhatTheBodyDoes() {
    assertThat(base.describe()).isEqualTo("POST a text to login.cgi");
    assertThat(base.describedAs("Asking for a certificate").describe())
        .isEqualTo("Asking for a certificate");
  }

  @Test
  @DisplayName("a body and its content type must not be blank")
  void neitherIsBlank() {
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CgiRequest.Body.of(Endpoint.LOGIN, "  ", CONTENT_TYPE))
        .withMessageContaining("not blank");
    assertThatIllegalArgumentException()
        .isThrownBy(() -> CgiRequest.Body.of(Endpoint.LOGIN, text, ""))
        .withMessageContaining("not blank");
  }

  @Test
  @DisplayName("each part of a body is required")
  void eachPartIsRequired() {
    var time = Duration.ofSeconds(1);

    assertThatNullPointerException()
        .isThrownBy(
            () -> new CgiRequest.Body(null, new LinkedHashMap<>(), text, CONTENT_TYPE, "", time))
        .withMessage("endpoint");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Body(
                    Endpoint.LOGIN, new LinkedHashMap<>(), null, CONTENT_TYPE, "", time))
        .withMessage("text");
    assertThatNullPointerException()
        .isThrownBy(
            () -> new CgiRequest.Body(Endpoint.LOGIN, new LinkedHashMap<>(), text, null, "", time))
        .withMessage("contentType");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Body(
                    Endpoint.LOGIN, new LinkedHashMap<>(), text, CONTENT_TYPE, null, time))
        .withMessage("purpose");
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CgiRequest.Body(
                    Endpoint.LOGIN, new LinkedHashMap<>(), text, CONTENT_TYPE, "", null))
        .withMessage("readTimeout");
  }
}
