package com.jbraddockm.link.cgi;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.xml.XmlElement;
import java.util.Map;
import java.util.Optional;

/** Turns an answer that is not the expected one into the right error. */
public final class ResponseFailures {

  private static final String STATUS = "Status";
  private static final String ERROR_STATUS = "Error";
  private static final String ERROR = "Error";
  private static final String ERROR_IDENTIFIER = "ErrorIdentifier";
  private static final String ERROR_DESCRIPTION = "ErrorDescription";
  private static final String PLAIN_ERROR = "The device reported an error";

  private ResponseFailures() {}

  /**
   * Finds an error the device reported inside its answer, as {@code <Status>Error</Status>} with an
   * optional {@code ErrorDescription}.
   *
   * @param response what the device answered
   * @return {@code COMMAND_FAILED} with the device's description, or empty if the device reported
   *     no error
   */
  public static Optional<ServiceError> reportedError(ResponseEnvelope response) {
    var payload = response.payload();
    if (!payload.childText(STATUS).orElse("").equalsIgnoreCase(ERROR_STATUS)) {
      return Optional.empty();
    }
    var identifier =
        errorText(payload, ERROR_IDENTIFIER).flatMap(ResponseFailures::integer).orElse(null);
    return Optional.of(
        deviceError(
            errorText(payload, ERROR_DESCRIPTION).orElse(null),
            identifier,
            Json.object("element", response.name())));
  }

  /**
   * Builds the error of a command that the device reported as failed: its description with secrets
   * removed, or a plain sentence when it gave none, and the number of the error when it gave one.
   *
   * @param description what the device said; may be {@code null} or empty
   * @param identifier the number of the error; {@code null} if there is none
   * @param details what else to say of the error; the number is added after it
   * @return {@code COMMAND_FAILED}
   */
  public static ServiceError deviceError(
      String description, Integer identifier, Map<String, Object> details) {
    var text = description == null ? "" : Scrub.forDisplay(description);
    var allDetails = Json.object();
    allDetails.putAll(details);
    if (identifier != null) {
      allDetails.put("errorIdentifier", identifier);
    }
    return new ServiceError(
        CoreErrorCode.COMMAND_FAILED, text.isEmpty() ? PLAIN_ERROR : text, allDetails);
  }

  /**
   * Looks for an error field: inside an {@code Error} element first, then as a direct child. This
   * is the one strategy for reading device errors from any answer.
   *
   * @param payload the payload element of the answer
   * @param name the name of the field ({@code ErrorIdentifier} or {@code ErrorDescription})
   * @return the text; empty when the field is absent
   */
  public static Optional<String> errorField(XmlElement payload, String name) {
    return payload.textAt(ERROR, name).or(() -> payload.childText(name));
  }

  private static Optional<String> errorText(XmlElement payload, String name) {
    return errorField(payload, name);
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.valueOf(text.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  /**
   * Explains that an answer is not XML at all.
   *
   * @param response what the device answered
   * @return {@code UNEXPECTED_RESPONSE} with the HTTP status and the content type
   */
  public static ServiceError notXml(CgiResponse response) {
    return new ServiceError(
        LinkErrorCode.UNEXPECTED_RESPONSE,
        "The device answered with something that is not XML (HTTP " + response.statusCode() + ")",
        Json.object("status", response.statusCode(), "contentType", response.contentType()));
  }

  /**
   * Explains why a response is not the expected element: either the device reported an error, or it
   * answered with something this version does not know.
   *
   * @param response what the device answered
   * @param expected the element the conversation was waiting for
   * @return the error the device reported, otherwise {@code UNEXPECTED_RESPONSE} naming the element
   *     received
   */
  public static ServiceError notExpected(ResponseEnvelope response, ResponseElement expected) {
    return reportedError(response)
        .orElseGet(
            () ->
                new ServiceError(
                    LinkErrorCode.UNEXPECTED_RESPONSE,
                    "Expected "
                        + expected.elementName()
                        + " but the device answered "
                        + response.name(),
                    Json.object("expected", expected.elementName(), "received", response.name())));
  }
}
