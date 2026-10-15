package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.CommandResult;
import java.util.Optional;

/**
 * A conversation whose answers are device envelopes. An answer that is not XML ends the
 * conversation with {@code UNEXPECTED_RESPONSE}; the subclass only sees well-formed envelopes.
 *
 * @param <R> the result type
 */
public abstract class XmlConversation<R extends CommandResult> implements Conversation<R> {

  /** Creates the conversation. */
  protected XmlConversation() {}

  /**
   * Continues after an answer that is a device envelope.
   *
   * @param response the parsed answer
   * @return the next step
   */
  protected abstract Step<R> onResponse(ResponseEnvelope response);

  @Override
  public final Step<R> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope) {
    return envelope
        .map(this::onResponse)
        .orElseGet(() -> new Step.Fail<>(ResponseFailures.notXml(response)));
  }
}
