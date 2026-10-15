package com.jbraddockm.core.daemon;

import com.jbraddockm.core.error.ServiceError;
import java.util.Map;

/** How the first command of a new session, logging in and reading the device, ended. */
public sealed interface Establishment {

  /**
   * The session is usable.
   *
   * @param data the fields of the {@code connect} result that follow the session id, for example
   *     {@code host}, {@code port} and {@code device}
   */
  record Established(Map<String, Object> data) implements Establishment {}

  /**
   * The session could not be established. No session exists afterwards.
   *
   * @param error what went wrong
   */
  record Rejected(ServiceError error) implements Establishment {}
}
