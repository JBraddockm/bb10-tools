package com.jbraddockm.core.daemon;

/**
 * A prepared command that needs no session, such as scanning for devices. Standalone commands run
 * concurrently with everything else.
 */
@FunctionalInterface
public interface StandaloneCommand {

  /**
   * Runs the command and answers the request. Must end the request through the reply; the server
   * reports an internal error if it does not.
   *
   * @param reply where to send events
   */
  void run(Reply reply);
}
