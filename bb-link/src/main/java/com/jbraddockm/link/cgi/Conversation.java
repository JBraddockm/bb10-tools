package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.CommandResult;
import java.util.Optional;

/**
 * The exchange of requests and responses that makes up one command, as a state machine.
 *
 * <p>A conversation is used once. It performs no input or output: it is told what the device
 * answered and says what to do next. Most conversations read XML and extend {@link
 * XmlConversation}; one that wants the answer untouched implements this interface directly.
 *
 * @param <R> the result type
 */
public interface Conversation<R extends CommandResult> {

  /**
   * Begins the conversation.
   *
   * @return normally {@link Step.Send} with the first request
   */
  Step<R> start();

  /**
   * Continues after the device answered.
   *
   * @param response the answer as it came, whatever its content
   * @param envelope the answer parsed as a device envelope, or empty if it is not XML
   * @return the next step
   */
  Step<R> onAnswer(CgiResponse response, Optional<ResponseEnvelope> envelope);
}
