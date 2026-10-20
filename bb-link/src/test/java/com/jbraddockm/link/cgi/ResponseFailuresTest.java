package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ResponseFailures}: errors reported by the device and answers that are not what
 * was expected.
 */
@DisplayName("ResponseFailures: errors that a device reports, and answers that are not expected")
class ResponseFailuresTest {

  private static ResponseEnvelope answer(String resource) {
    return ResponseEnvelope.parse(Resources.text(ResponseFailuresTest.class, resource));
  }

  @Nested
  @DisplayName("reportedError")
  class ReportedError {

    @Test
    @DisplayName("reads an error that has its number and description inside an Error element")
    void readsANestedError() {
      var error = ResponseFailures.reportedError(answer("response-failures--error-nested.xml"));

      assertThat(error)
          .hasValueSatisfying(
              failure -> {
                assertThat(failure.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
                assertThat(failure.message()).isEqualTo("Error - insert certificate failed.");
                assertThat(failure.details())
                    .containsEntry("element", "Pairing")
                    .containsEntry("errorIdentifier", 7);
              });
    }

    @Test
    @DisplayName("reads an error that has its number and description beside the status")
    void readsAFlatError() {
      var error = ResponseFailures.reportedError(answer("response-failures--error-flat.xml"));

      assertThat(error)
          .hasValueSatisfying(
              failure -> {
                assertThat(failure.message()).isEqualTo("Flat description");
                assertThat(failure.details())
                    .containsEntry("element", "Update")
                    .containsEntry("errorIdentifier", 12);
              });
    }

    @Test
    @DisplayName("prefers the Error element to what is beside the status")
    void prefersTheNestedError() {
      var error =
          ResponseFailures.reportedError(answer("response-failures--nested-before-flat.xml"));

      assertThat(error)
          .hasValueSatisfying(failure -> assertThat(failure.message()).isEqualTo("Nested one"));
    }

    @Test
    @DisplayName("takes the status in any case")
    void takesTheStatusInAnyCase() {
      var error =
          ResponseFailures.reportedError(answer("response-failures--error-lowercase-status.xml"));

      assertThat(error)
          .hasValueSatisfying(failure -> assertThat(failure.message()).isEqualTo("lower"));
    }

    @ParameterizedTest(name = "{0} is no error")
    @ValueSource(strings = {"response-failures--success.xml", "response-failures--no-status.xml"})
    @DisplayName("gives nothing for an answer that does not say Error")
    void givesNothingForOtherAnswers(String resource) {
      assertThat(ResponseFailures.reportedError(answer(resource))).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "response-failures--error-without-description.xml",
          "response-failures--error-blank-description.xml",
          "response-failures--error-only-cookie.xml"
        })
    @DisplayName("says that the device reported an error when it gave no description to show")
    void saysThatAnErrorWasReported(String resource) {
      var error = ResponseFailures.reportedError(answer(resource));

      assertThat(error)
          .hasValueSatisfying(
              failure -> assertThat(failure.message()).isEqualTo("The device reported an error"));
    }

    @Test
    @DisplayName("leaves out the error number when it is not a number")
    void leavesOutANumberThatIsNotANumber() {
      var error =
          ResponseFailures.reportedError(
              answer("response-failures--error-identifier-not-a-number.xml"));

      assertThat(error)
          .hasValueSatisfying(
              failure -> {
                assertThat(failure.message()).isEqualTo("Odd");
                assertThat(failure.details()).doesNotContainKey("errorIdentifier");
              });
    }

    @Test
    @DisplayName("leaves out the number when there is none")
    void leavesOutAMissingNumber() {
      var error =
          ResponseFailures.reportedError(answer("response-failures--error-lowercase-status.xml"));

      assertThat(error)
          .hasValueSatisfying(
              failure -> assertThat(failure.details()).doesNotContainKey("errorIdentifier"));
    }

    @Test
    @DisplayName("takes the cookie that a device echoes out of the description")
    void removesTheCookieEcho() {
      var error =
          ResponseFailures.reportedError(answer("response-failures--error-with-cookie.xml"));

      assertThat(error)
          .hasValueSatisfying(
              failure -> {
                assertThat(failure.message()).isEqualTo("Denied");
                assertThat(failure.message()).doesNotContain("abc123").doesNotContain("def456");
              });
    }
  }

  @Nested
  @DisplayName("notXml")
  class NotXml {

    @Test
    @DisplayName("says that the answer is not XML, with the HTTP status and the content type")
    void saysWhatTheAnswerWas() {
      var error = ResponseFailures.notXml(htmlError());

      assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(error.message())
          .isEqualTo("The device answered with something that is not XML (HTTP 502)");
      assertThat(error.details())
          .containsEntry("status", 502)
          .containsEntry("contentType", "text/html");
    }
  }

  @Nested
  @DisplayName("notExpected")
  class NotExpected {

    @Test
    @DisplayName("says what was expected and what the device answered instead")
    void saysWhatWasExpected() {
      var error =
          ResponseFailures.notExpected(
              answer("response-envelope--known-element.xml"), ResponseElement.AUTH);

      assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(error.message()).isEqualTo("Expected Auth but the device answered DevicePIN");
      assertThat(error.details())
          .containsEntry("expected", "Auth")
          .containsEntry("received", "DevicePIN");
    }

    @Test
    @DisplayName(
        "gives the error that the device reported, if it reported one, and not the mismatch")
    void givesTheReportedErrorFirst() {
      var error =
          ResponseFailures.notExpected(
              answer("response-failures--error-nested.xml"), ResponseElement.AUTH);

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo("Error - insert certificate failed.");
    }
  }

  private static CgiResponse htmlError() {
    return new CgiResponse(
        502,
        "text/html",
        Resources.text(ResponseFailuresTest.class, "cgi-response--html-error.html"),
        null);
  }
}
