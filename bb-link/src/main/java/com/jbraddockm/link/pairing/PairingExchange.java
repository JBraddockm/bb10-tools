package com.jbraddockm.link.pairing;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import java.util.Map;
import java.util.Optional;

/**
 * One request to {@code pairing.cgi} and the reading of its answer, which the three operations
 * share: a success is the answer, an error is a failed command, and anything else is not taken for
 * a success.
 */
final class PairingExchange implements Conversation<PairingReply> {

  private final CgiRequest request;

  PairingExchange(CgiRequest request) {
    this.request = request;
  }

  @Override
  public Step<PairingReply> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<PairingReply> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope) {
    if (envelope.isEmpty()) {
      return new Step.Fail<>(
          ResponseFailures.notXml(response), Optional.of(PairingReply.unreadable(response)));
    }
    var reply = PairingReply.read(response, envelope.get());
    return switch (reply.status()) {
      case SUCCESS -> new Step.Finish<>(reply);
      case ERROR ->
          new Step.Fail<>(
              new ServiceError(
                  CoreErrorCode.COMMAND_FAILED,
                  reply.errorDescription() == null
                      ? "The device reported an error"
                      : reply.errorDescription(),
                  reply.errorIdentifier() == null
                      ? Map.of()
                      : Json.object("errorIdentifier", reply.errorIdentifier())),
              Optional.of(reply));
      case READY, IN_PROGRESS, DENIED, PASSWORD_CHALLENGE, UNKNOWN ->
          new Step.Fail<>(
              new ServiceError(
                  LinkErrorCode.UNEXPECTED_RESPONSE,
                  "The device answered pairing.cgi with a status that is not a success or an error",
                  Json.object("status", reply.statusText() == null ? "" : reply.statusText())),
              Optional.of(reply));
    };
  }
}
