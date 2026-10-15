package com.jbraddockm.link.cgi;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.internal.Scrub;
import java.util.Optional;

/** Turns an answer that is not the expected one into the right error. */
public final class ResponseFailures {

  private static final String STATUS = "Status";
  private static final String ERROR_STATUS = "Error";
  private static final String ERROR_DESCRIPTION = "ErrorDescription";

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
    var description =
        payload
            .childText(ERROR_DESCRIPTION)
            .map(Scrub::forDisplay)
            .filter(text -> !text.isEmpty())
            .orElse("The device reported an error");
    return Optional.of(
        new ServiceError(
            CoreErrorCode.COMMAND_FAILED, description, Json.object("element", response.name())));
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
