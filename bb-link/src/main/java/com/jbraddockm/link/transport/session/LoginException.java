package com.jbraddockm.link.transport.session;

import java.io.Serial;

/** A login that did not succeed. Each subtype is a different cause with its own error code. */
abstract sealed class LoginException extends Exception
    permits PasswordRejectedException,
        InvalidPasswordException,
        ClockOutOfSyncException,
        PasswordRequiredException,
        UnusableLoginResponseException {

  @Serial private static final long serialVersionUID = 1L;

  LoginException(String message) {
    super(message, null, false, false); // a failure to report, not a place to debug
  }
}
