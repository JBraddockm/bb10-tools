package com.jbraddockm.link.session;

import java.io.Serial;

/**
 * The device asked for a password during the login and none was given. No answer was sent, so no
 * failed attempt was counted against the device's lock.
 */
public final class PasswordRequiredException extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  PasswordRequiredException() {
    super("The device asks for a password and none was given; no login attempt was made");
  }
}
