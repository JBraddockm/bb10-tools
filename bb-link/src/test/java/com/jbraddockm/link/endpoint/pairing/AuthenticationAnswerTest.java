package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.transport.cgi.BodyFormat;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.endpoint.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link AuthenticationAnswer}: reading the answer to a certificate request. */
@DisplayName("AuthenticationAnswer: what login.cgi answered to a request for a certificate")
class AuthenticationAnswerTest {

  private static final String LEAF =
      Resources.text(TestKeys.class, "certificate--device-issued.pem");

  private static AuthenticationAnswer pemAnswer(String resource) {
    var text = Resources.text(AuthenticationAnswerTest.class, resource);
    return AuthenticationAnswer.read(
        new CgiResponse(200, "application/octet-stream", text, null), null);
  }

  private static AuthenticationAnswer xmlAnswer(String resource) {
    var text = Resources.text(AuthenticationAnswerTest.class, resource);
    return AuthenticationAnswer.read(
        new CgiResponse(200, "text/xml", text, null), ResponseEnvelope.parse(text));
  }

  @Nested
  @DisplayName("the answer of a device that signs")
  class Signed {

    @Test
    @DisplayName("is the certificate as it is written, and nothing else: no status, no error")
    void isABareCertificate() {
      var answer = pemAnswer("authentication-answer--certificate.pem");

      assertThat(answer.certificate()).contains(LEAF);
      assertThat(answer.statusText()).isNull();
      assertThat(answer.errorIdentifier()).isNull();
      assertThat(answer.errorDescription()).isNull();
      assertThat(answer.contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("is reported as text, since it is not XML or JSON")
    void isReportedAsText() {
      assertThat(pemAnswer("authentication-answer--certificate.pem").body().format())
          .isEqualTo(BodyFormat.TEXT);
    }

    @Test
    @DisplayName("has the certificate even when there is text before and after it")
    void findsTheCertificateAmongText() {
      assertThat(pemAnswer("authentication-answer--certificate-after-noise.txt").certificate())
          .contains(LEAF);
    }

    @Test
    @DisplayName("has the first certificate, when there are two")
    void hasTheFirstOfTwo() {
      assertThat(pemAnswer("authentication-answer--two-certificates.pem").certificate())
          .hasValueSatisfying(certificate -> assertThat(certificate).isEqualTo(LEAF));
    }
  }

  @Nested
  @DisplayName("the answer of a device that refuses")
  class Refused {

    @Test
    @DisplayName(
        "has the status, the number and the description of the error in the CgiAuth element: the"
            + " real error 260")
    void hasTheError() {
      var answer = xmlAnswer("authentication-answer--content-type-error.xml");

      assertThat(answer.statusText()).isEqualTo("Error");
      assertThat(answer.errorIdentifier()).isEqualTo(260);
      assertThat(answer.errorDescription())
          .isEqualTo("Expected ContentType of application/octet-stream");
      assertThat(answer.certificate()).isEmpty();
      assertThat(answer.body().format()).isEqualTo(BodyFormat.XML);
    }

    @Test
    @DisplayName("has no description when the device gave none")
    void hasNoDescription() {
      var answer = xmlAnswer("authentication-answer--error-without-description.xml");

      assertThat(answer.statusText()).isEqualTo("Error");
      assertThat(answer.errorDescription()).isNull();
      assertThat(answer.errorIdentifier()).isNull();
    }

    @Test
    @DisplayName("has no error number when what the device gave is not a number")
    void hasNoNumberWhenItIsNotANumber() {
      var answer = xmlAnswer("authentication-answer--error-identifier-not-a-number.xml");

      assertThat(answer.errorIdentifier()).isNull();
      assertThat(answer.errorDescription()).isEqualTo("Odd");
    }
  }

  @Nested
  @DisplayName("an answer that has neither")
  class Neither {

    @Test
    @DisplayName(
        "is an answer with no certificate, no status and no error, when it is not XML and not a"
            + " certificate")
    void notXmlAndNotACertificate() {
      var answer = pemAnswer("authentication-answer--not-xml-not-pem.txt");

      assertThat(answer.certificate()).isEmpty();
      assertThat(answer.statusText()).isNull();
    }

    @Test
    @DisplayName(
        "is an answer with a status and no certificate, when the XML says Success and holds none")
    void successWithoutACertificate() {
      var answer = xmlAnswer("authentication-answer--success-without-certificate.xml");

      assertThat(answer.statusText()).isEqualTo("Success");
      assertThat(answer.certificate()).isEmpty();
    }
  }

  @Test
  @DisplayName(
      "toJson gives the fields of the answer, the body in its own format, and null for what is not"
          + " there")
  void toJsonGivesTheFields() {
    var json = pemAnswer("authentication-answer--certificate.pem").toJson();

    assertThat(json.keySet())
        .containsExactly(
            "contentType",
            "status",
            "certificate",
            "errorIdentifier",
            "errorDescription",
            "bodyFormat",
            "body");
    assertThat(json)
        .containsEntry("certificate", LEAF)
        .containsEntry("bodyFormat", "text")
        .containsEntry("status", null);
  }
}
