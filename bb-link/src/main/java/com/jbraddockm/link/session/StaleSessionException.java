package com.jbraddockm.link.session;

import java.io.Serial;

/** The device no longer accepts the login cookie. */
final class StaleSessionException extends Exception {

  @Serial private static final long serialVersionUID = 1L;

  private final boolean firstExchange;

  StaleSessionException(boolean firstExchange) {
    super("the device does not accept the session", null, false, false);
    this.firstExchange = firstExchange;
  }

  /**
   * Tells whether this was the first request of the command. Only then has nothing been changed on
   * the device yet, so only then is a new login followed by a second try safe.
   */
  boolean firstExchange() {
    return firstExchange;
  }
}
