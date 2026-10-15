package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.ServiceError;
import java.util.Optional;

/**
 * What a conversation wants to happen next.
 *
 * @param <R> the result type of the conversation
 */
public sealed interface Step<R extends CommandResult> {

  /**
   * Send a request and report the response back.
   *
   * @param request the request to send
   * @param <R> the result type
   */
  record Send<R extends CommandResult>(CgiRequest request) implements Step<R> {}

  /**
   * The command succeeded.
   *
   * @param result the result
   * @param <R> the result type
   */
  record Finish<R extends CommandResult>(R result) implements Step<R> {}

  /**
   * The command failed. The answer that led to the failure can be kept, so that a client sees what
   * the device said as well as that it was a failure.
   *
   * @param error what went wrong
   * @param partial the answer that caused the failure, if it is worth keeping
   * @param <R> the result type
   */
  record Fail<R extends CommandResult>(ServiceError error, Optional<R> partial) implements Step<R> {

    /**
     * Creates a failure without an answer.
     *
     * @param error what went wrong
     */
    public Fail(ServiceError error) {
      this(error, Optional.empty());
    }
  }
}
