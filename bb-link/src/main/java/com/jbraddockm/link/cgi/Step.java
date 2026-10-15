package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.error.ServiceError;

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
   * The command failed.
   *
   * @param error what went wrong
   * @param <R> the result type
   */
  record Fail<R extends CommandResult>(ServiceError error) implements Step<R> {}
}
