package com.jbraddockm.core.daemon;

/**
 * A prepared command that runs on a session. It was built from the request's parameters on the
 * reading thread, so every parameter problem has already been reported before it is queued.
 *
 * @param <S> the session type
 */
@FunctionalInterface
public interface SessionCommand<S extends ServiceSession> {

  /**
   * Runs the command and answers the request. Must end the request through the reply; the server
   * reports an internal error if it does not.
   *
   * @param session the session the command belongs to
   * @param reply where to send events
   */
  void run(S session, Reply reply);
}
