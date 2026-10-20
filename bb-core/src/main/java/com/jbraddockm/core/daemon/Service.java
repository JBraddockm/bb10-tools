package com.jbraddockm.core.daemon;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.List;

/**
 * What a tool provides to the daemon server. The server handles the protocol, the sessions and the
 * ordering; the service decides what the methods do.
 *
 * <p>The methods {@code connect}, {@code disconnect} and {@code shutdown} belong to the server and
 * must not be declared by a service.
 *
 * @param <S> the session type
 */
public interface Service<S extends ServiceSession> {

  /**
   * Identifies the tool.
   *
   * @return the name and version reported in {@code hello}
   */
  ServiceInfo info();

  /**
   * Lists the methods beyond {@code connect}, {@code disconnect} and {@code shutdown}.
   *
   * @return the methods in the order they are advertised
   */
  List<CommandSpec<S>> commands();

  /**
   * Creates the session for a {@code connect} request. Validates the parameters and performs no
   * input or output.
   *
   * @param params the parameters of the {@code connect} request
   * @return a new, not yet established session
   * @throws InvalidArgumentException if a parameter is unacceptable
   */
  S open(RequestParams params);

  /**
   * Runs the first command of a session: logging in and reading the device. Called on the session's
   * own queue.
   *
   * @param session the session created by {@link #open}
   * @param params the parameters of the {@code connect} request that created the session
   * @param reply receives log events while the session is established
   * @return whether the session is usable
   */
  Establishment establish(S session, RequestParams params, Reply reply);
}
