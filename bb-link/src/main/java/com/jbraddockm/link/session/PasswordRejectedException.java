package com.jbraddockm.link.session;

import java.io.Serial;

/** The device answered {@code Denied} and says how many attempts were used. */
public final class PasswordRejectedException extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  private final int failedAttempts;
  private final int remainingAttempts;

  PasswordRejectedException(int failedAttempts, int remainingAttempts) {
    super(LoginMessages.passwordRejected(failedAttempts, remainingAttempts));
    this.failedAttempts = failedAttempts;
    this.remainingAttempts = remainingAttempts;
  }

  /**
   * Returns how many attempts have failed.
   *
   * @return the count, including this one
   */
  public int failedAttempts() {
    return failedAttempts;
  }

  /**
   * Returns how many attempts are left before the device locks.
   *
   * @return the remaining attempts
   */
  public int remainingAttempts() {
    return remainingAttempts;
  }

  /**
   * Tells whether the device refuses further attempts. The lock is lifted on the device itself.
   *
   * @return {@code true} if no attempts remain
   */
  public boolean isLocked() {
    return remainingAttempts == 0;
  }
}
