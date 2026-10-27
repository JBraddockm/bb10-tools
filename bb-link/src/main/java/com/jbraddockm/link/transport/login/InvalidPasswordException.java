package com.jbraddockm.link.transport.login;

import java.io.Serial;

/** The login ended without a session cookie and without a count of attempts. */
final class InvalidPasswordException extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  InvalidPasswordException() {
    super(LoginMessages.invalidPassword());
  }
}
