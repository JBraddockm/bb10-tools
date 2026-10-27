package com.jbraddockm.link.transport.login;

import java.io.Serial;

/** The device answered the login in a way that cannot be interpreted. */
final class UnusableLoginResponseException extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  private final String extra;

  UnusableLoginResponseException(String extra) {
    super(LoginMessages.systemError());
    this.extra = extra;
  }

  /**
   * Returns what exactly was wrong with the answer.
   *
   * @return a short explanation
   */
  public String extra() {
    return extra;
  }
}
