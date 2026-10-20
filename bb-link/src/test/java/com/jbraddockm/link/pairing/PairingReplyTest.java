package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.cgi.BodyFormat;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseStatus;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link PairingReply}: reading the answer of {@code pairing.cgi}. */
@DisplayName("PairingReply: what pairing.cgi answered")
class PairingReplyTest {

  private static final String AUTHORITY =
      Resources.text(TestKeys.class, "certificate--device-authority-ec.pem").strip();

  private static PairingReply read(String resource) {
    var text = Resources.text(PairingReplyTest.class, resource);
    return PairingReply.read(
        new CgiResponse(200, "text/xml", text, null), ResponseEnvelope.parse(text));
  }

  @Nested
  @DisplayName("the answer of a device that takes the authority")
  class Accepted {

    @Test
    @DisplayName(
        "is a success when it has the device's authority and no status, which is what a device"
            + " really answers")
    void anAuthorityAndNoStatusIsASuccess() {
      var reply = read("pairing-reply--authority-accepted.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.SUCCESS);
      assertThat(reply.statusText()).isNull();
      assertThat(reply.deviceAuthority()).contains(AUTHORITY);
      assertThat(reply.errorIdentifier()).isNull();
      assertThat(reply.errorDescription()).isNull();
    }

    @Test
    @DisplayName("also has the authority from a Certificate element, beside a status of Success")
    void anAuthorityInACertificateElement() {
      var reply = read("pairing-reply--certificate-element.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.SUCCESS);
      assertThat(reply.statusText()).isEqualTo("Success");
      assertThat(reply.deviceAuthority()).contains(AUTHORITY);
    }

    @Test
    @DisplayName("takes the authority from the text when the Certificate element is blank")
    void aBlankCertificateElement() {
      assertThat(read("pairing-reply--blank-certificate-element.xml").deviceAuthority())
          .contains(AUTHORITY);
    }

    @Test
    @DisplayName(
        "is a success without an authority when the status says Success, and says that it has none")
    void aSuccessWithoutAnAuthority() {
      var reply = read("pairing-reply--success-without-authority.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.SUCCESS);
      assertThat(reply.deviceAuthority()).isEmpty();
    }
  }

  @Nested
  @DisplayName("the answer of a device that refuses")
  class Refused {

    @Test
    @DisplayName("has the error status, the number and the description of the error")
    void hasTheError() {
      var reply = read("pairing-reply--error.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.ERROR);
      assertThat(reply.statusText()).isEqualTo("Error");
      assertThat(reply.errorIdentifier()).isEqualTo(7);
      assertThat(reply.errorDescription()).isEqualTo("Error - insert certificate failed.");
      assertThat(reply.deviceAuthority()).isEmpty();
    }

    @Test
    @DisplayName("has no description when the device gave none")
    void hasNoDescriptionWhenNoneWasGiven() {
      var reply = read("pairing-reply--error-without-description.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.ERROR);
      assertThat(reply.errorDescription()).isNull();
      assertThat(reply.errorIdentifier()).isNull();
    }

    @Test
    @DisplayName("has no error number when what the device gave is not a number")
    void hasNoNumberWhenItIsNotANumber() {
      var reply = read("pairing-reply--error-identifier-not-a-number.xml");

      assertThat(reply.errorIdentifier()).isNull();
      assertThat(reply.errorDescription()).isEqualTo("Odd");
    }

    @Test
    @DisplayName(
        "stays an error, though the answer has an authority: only an answer with no status is made"
            + " a success")
    void anErrorWithAnAuthorityStaysAnError() {
      var reply = read("pairing-reply--error-with-authority.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.ERROR);
      assertThat(reply.deviceAuthority()).contains(AUTHORITY);
    }
  }

  @Nested
  @DisplayName("an answer that is neither")
  class Unclear {

    @Test
    @DisplayName("is not a success when it has no status and no authority")
    void noStatusAndNoAuthority() {
      var reply = read("pairing-reply--no-status-text-only.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.UNKNOWN);
      assertThat(reply.statusText()).isNull();
      assertThat(reply.deviceAuthority()).isEmpty();
    }

    @Test
    @DisplayName(
        "is not a success when it has an authority and no status but an error number: that is an"
            + " error")
    void anAuthorityWithAnErrorNumberIsNotASuccess() {
      var reply = read("pairing-reply--no-status-authority-and-error-identifier.xml");

      assertThat(reply.status()).isEqualTo(ResponseStatus.UNKNOWN);
      assertThat(reply.errorIdentifier()).isEqualTo(7);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
      "pairing-reply--ready.xml, READY",
      "pairing-reply--denied.xml, DENIED",
      "pairing-reply--in-progress.xml, IN_PROGRESS",
      "pairing-reply--strange.xml, UNKNOWN"
    })
    @DisplayName("keeps the status that the device gave, as a word that is known or UNKNOWN")
    void keepsTheStatus(String resource, ResponseStatus expected) {
      assertThat(read(resource).status()).isEqualTo(expected);
    }

    @Test
    @DisplayName(
        "unreadable is an answer that was not XML: no status, no authority, and the body as text")
    void unreadable() {
      var text = Resources.text(PairingReplyTest.class, "pairing-reply--not-xml.txt");

      var reply = PairingReply.unreadable(new CgiResponse(200, "text/html", text, null));

      assertThat(reply.status()).isEqualTo(ResponseStatus.UNKNOWN);
      assertThat(reply.deviceAuthority()).isEmpty();
      assertThat(reply.body().format()).isEqualTo(BodyFormat.TEXT);
      assertThat(reply.body().value()).isEqualTo(text);
      assertThat(reply.contentType()).isEqualTo("text/html");
    }
  }

  @Test
  @DisplayName(
      "toJson gives the fields of the answer, the body in its own format, and null for what is not"
          + " there")
  void toJsonGivesTheFields() {
    var json = read("pairing-reply--authority-accepted.xml").toJson();

    assertThat(json.keySet())
        .containsExactly(
            "contentType",
            "status",
            "deviceAuthority",
            "errorIdentifier",
            "errorDescription",
            "bodyFormat",
            "body");
    assertThat(json)
        .containsEntry("contentType", "text/xml")
        .containsEntry("status", null)
        .containsEntry("deviceAuthority", AUTHORITY)
        .containsEntry("bodyFormat", "xml");
    assertThat(((Map<?, ?>) json.get("body")).containsKey("Pairing")).isTrue();
  }

  @Test
  @DisplayName("keeps the content type of the answer")
  void keepsTheContentType() {
    var text = Resources.text(PairingReplyTest.class, "pairing-reply--error.xml");

    var reply =
        PairingReply.read(
            new CgiResponse(200, "application/xml", text, null), ResponseEnvelope.parse(text));

    assertThat(reply.contentType()).isEqualTo("application/xml");
  }
}
