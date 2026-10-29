package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimStatus;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.util.Optional;
import java.util.Set;

/**
 * One contact-photo request and what to make of its answer. Unlike the other PIM commands, the
 * device's answer here may be binary: when it streamed an image to the file, that is the result;
 * otherwise it answered with a status (JSON or text) — "not modified", an error — which is read the
 * same way the rest of {@code pimsync.cgi} is. The device says no with 1001, 1013 or 1015; those
 * end the command and keep the answer. Any other status, including {@code 1016 No Changes} for an
 * unchanged photo, is a result.
 */
final class PhotoConversation implements Conversation<PimPhoto> {

  private static final Set<Integer> REJECTIONS = Set.of(1001, 1013, 1015);

  /** The wire word for the photo service, which is not one of the item services. */
  static final String SERVICE = "photos";

  private final CgiRequest request;

  PhotoConversation(CgiRequest request) {
    this.request = request;
  }

  @Override
  public Step<PimPhoto> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<PimPhoto> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() != null) {
      var downloaded = response.downloaded();
      return new Step.Finish<>(
          new PimPhoto(
              response.statusCode(),
              response.contentType(),
              Optional.of(downloaded.file()),
              downloaded.bytes(),
              null,
              null));
    }
    var status = PimStatus.read(response.body());
    var code = status.map(PimStatus::code).orElse(null);
    var message = status.map(PimStatus::message).orElse(null);
    var answer =
        new PimPhoto(
            response.statusCode(), response.contentType(), Optional.empty(), 0, code, message);
    if (status.isPresent() && REJECTIONS.contains(status.get().code())) {
      return new Step.Fail<>(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              message == null || message.isBlank() ? "The device rejected the request" : message,
              Json.object("code", status.get().code())),
          Optional.of(answer));
    }
    return new Step.Finish<>(answer);
  }
}
