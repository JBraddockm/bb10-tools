package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.CommandResult;

/**
 * The exchange of requests and responses that makes up one command, as a state machine.
 *
 * <p>A conversation is used once. It performs no input or output: it is told what the device
 * answered and says what to do next.
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
   * Continues after a response.
   *
   * @param response the device's answer to the last request
   * @return the next step
   */
  Step<R> onResponse(ResponseEnvelope response);
}
