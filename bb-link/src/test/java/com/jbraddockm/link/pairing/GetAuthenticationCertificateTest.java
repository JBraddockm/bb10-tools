package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.QueryParameter;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link GetAuthenticationCertificate}: the request sent to {@code login.cgi}. */
@DisplayName(
    "GetAuthenticationCertificate: the request for a certificate for the login on port 8443")
class GetAuthenticationCertificateTest {

  private static final String REQUEST_PEM =
      Resources.text(
          GetAuthenticationCertificateTest.class, "get-authentication-certificate--request.pem");

  private static CgiRequest.Body requestOf(GetAuthenticationCertificate command) {
    var step = command.conversation().start();
    assertThat(step).isInstanceOf(Step.Send.class);
    return (CgiRequest.Body) ((Step.Send<?>) step).request();
  }

  @Test
  @DisplayName(
      "is a body to login.cgi that is the request as it was given, in application/octet-stream")
  void sendsTheRequestAsTheBody() {
    var body = requestOf(new GetAuthenticationCertificate(REQUEST_PEM));

    assertThat(body.endpoint()).isEqualTo(Endpoint.LOGIN);
    assertThat(body.text()).isEqualTo(REQUEST_PEM);
    assertThat(body.contentType()).isEqualTo("application/octet-stream");
  }

  @Test
  @DisplayName("has request_version=3 and cgiauth=1, in this order")
  void hasTheQuery() {
    var body = requestOf(new GetAuthenticationCertificate(REQUEST_PEM));

    assertThat(body.query())
        .containsExactly(
            Map.entry(QueryParameter.REQUEST_VERSION, "3"),
            Map.entry(QueryParameter.CGI_AUTH, "1"));
    assertThat(body.pathAndQuery()).isEqualTo("/cgi-bin/login.cgi?request_version=3&cgiauth=1");
  }

  @Test
  @DisplayName("says what it does")
  void describesItself() {
    assertThat(requestOf(new GetAuthenticationCertificate(REQUEST_PEM)).describe())
        .isEqualTo("Asking the device to sign a certificate request");
  }

  @Test
  @DisplayName("changes the device, so it is not a read-only command and is never repeated")
  void changesTheDevice() {
    assertThat(new GetAuthenticationCertificate(REQUEST_PEM).readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a request")
  @NullAndEmptySource
  @ValueSource(strings = {"  ", "\n"})
  @DisplayName("refuses a request that is missing or blank")
  void refusesABlankRequest(String request) {
    assertThatThrownBy(() -> new GetAuthenticationCertificate(request))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("csr"));
  }
}
