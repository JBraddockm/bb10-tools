package com.jbraddockm.link.pimsync;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.QueryParameter;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import java.util.Optional;
import java.util.Set;

/**
 * One request to {@code pimsync.cgi} and what to make of its answer.
 *
 * <p>The device says no with status 1001 (invalid parameters), 1013 (unauthorised) or 1015
 * (unsupported service or operation). Those end the command with {@code COMMAND_FAILED}, and the
 * answer is kept, so that the client still sees the body. 1013 has been seen **[verified]**: the
 * answer to a {@code get} for a workstation the device does not know is "received HTTP 401/403 from
 * server", which is the device's own refusal and not a lost session. A lost session is told apart
 * by the text {@code DTM NOT LOGGED IN} and is dealt with by the session, which logs in again. 1001
 * and 1015 are **[RESEARCH]**. Any other answer is a result.
 */
final class PimSyncConversation implements Conversation<PimAnswer> {

  private static final int INVALID_PARAMETERS = 1001;
  private static final int UNAUTHORIZED = 1013;
  private static final int UNSUPPORTED = 1015;

  /** The statuses with which the device says no. */
  private static final Set<Integer> REJECTIONS =
      Set.of(INVALID_PARAMETERS, UNAUTHORIZED, UNSUPPORTED);

  private final CgiRequest.Get request;
  private final PimCommand command;
  private final PimService service;

  /**
   * Creates the conversation.
   *
   * @param command the command to send
   * @param service the kind of items, or {@code null}
   * @param purpose what the request does, for the log
   * @param parameters the parameters after {@code command}, in the order they are sent
   */
  PimSyncConversation(
      PimCommand command,
      PimService service,
      String purpose,
      java.util.function.UnaryOperator<CgiRequest.Get> parameters) {
    this.command = command;
    this.service = service;
    // An empty answer is an answer: this program answers a request without a command with
    // nothing, and says in words when there is no session.
    this.request =
        parameters.apply(
            CgiRequest.Get.to(Endpoint.PIM_SYNC)
                .with(QueryParameter.COMMAND, command)
                .describedAs(purpose)
                .expectingNoBody());
  }

  @Override
  public Step<PimAnswer> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<PimAnswer> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope) {
    var status = PimStatus.read(response.body());
    var answer =
        new PimAnswer(
            command,
            service,
            response.statusCode(),
            status.map(PimStatus::code).orElse(null),
            status.map(PimStatus::message).orElse(null),
            response.body());
    if (status.isPresent() && REJECTIONS.contains(status.get().code())) {
      var message = status.get().message();
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
