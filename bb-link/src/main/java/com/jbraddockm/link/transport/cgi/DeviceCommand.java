package com.jbraddockm.link.transport.cgi;

import com.jbraddockm.core.command.CommandResult;

/**
 * A command a session can run against the device.
 *
 * @param <R> the result type
 */
public interface DeviceCommand<R extends CommandResult> {

  /**
   * Creates the conversation for one attempt. Called again if the session retries.
   *
   * @return a fresh conversation
   */
  Conversation<R> conversation();

  /**
   * Tells whether the command only reads. Only read-only commands are tried a second time after a
   * connection dropped, because repeating a command that changes the device could change it twice.
   *
   * @return {@code true} if running the command twice is harmless
   */
  boolean readOnly();
}
