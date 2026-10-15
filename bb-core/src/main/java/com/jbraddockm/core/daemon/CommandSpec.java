package com.jbraddockm.core.daemon;

import java.util.function.Function;

/**
 * Declares one method of a service: its wire name and how to prepare a command from the request's
 * parameters.
 *
 * @param <S> the session type of the service
 */
public sealed interface CommandSpec<S extends ServiceSession> {

  /**
   * Returns the method name as it appears in requests.
   *
   * @return the wire name
   */
  String wireName();

  /**
   * A method that runs on a session and needs the {@code session} parameter.
   *
   * @param wireName the method name
   * @param factory validates the parameters and prepares the command
   * @param <S> the session type
   */
  record SessionScoped<S extends ServiceSession>(
      String wireName, Function<RequestParams, SessionCommand<S>> factory)
      implements CommandSpec<S> {}

  /**
   * A method that needs no session.
   *
   * @param wireName the method name
   * @param factory validates the parameters and prepares the command
   * @param <S> the session type
   */
  record Standalone<S extends ServiceSession>(
      String wireName, Function<RequestParams, StandaloneCommand> factory)
      implements CommandSpec<S> {}
}
