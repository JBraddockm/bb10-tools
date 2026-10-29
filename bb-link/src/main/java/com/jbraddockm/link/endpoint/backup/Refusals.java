package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import java.util.Optional;

/**
 * The refusal check the backup and restore conversations share: an answer is accepted only when it
 * is a device envelope, reports no error, and is the element the step expects.
 */
final class Refusals {

  private Refusals() {}

  /**
   * Returns the error to fail a step with when the answer is not a clean, expected success, or
   * empty when it is fine.
   *
   * @param response the answer as it came
   * @param envelope the answer parsed as a device envelope, or {@code null} if it is not XML
   * @param expected the element the step is waiting for
   * @return the refusal error, or empty when the answer is the expected element without an error
   */
  static Optional<ServiceError> check(
      CgiResponse response, ResponseEnvelope envelope, ResponseElement expected) {
    if (envelope == null) {
      return Optional.of(ResponseFailures.notXml(response));
    }
    var reported = ResponseFailures.reportedError(envelope);
    if (reported.isPresent()) {
      return reported;
    }
    if (envelope.element().orElse(null) != expected) {
      return Optional.of(ResponseFailures.notExpected(envelope, expected));
    }
    return Optional.empty();
  }
}
