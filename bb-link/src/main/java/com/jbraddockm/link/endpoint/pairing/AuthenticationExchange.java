package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import java.util.Map;
import java.util.Optional;

/**
 * One request to {@code login.cgi} for a certificate and the reading of its answer. An error that
 * the device reports is a failed command; anything else is the answer, for the caller to judge,
 * because what a success looks like has not been seen.
 */
final class AuthenticationExchange implements Conversation<AuthenticationAnswer> {

  private final CgiRequest request;

  AuthenticationExchange(CgiRequest request) {
    this.request = request;
  }

  @Override
  public Step<AuthenticationAnswer> start() {
    return new Step.Send<>(request);
  }

  @Override
  public Step<AuthenticationAnswer> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    var answer = AuthenticationAnswer.read(response, envelope);
    if ("Error".equalsIgnoreCase(answer.statusText())) {
      return new Step.Fail<>(
          ResponseFailures.deviceError(
              answer.errorDescription(), answer.errorIdentifier(), Map.of()),
          Optional.of(answer));
    }
    return new Step.Finish<>(answer);
  }
}
