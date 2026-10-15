package com.jbraddockm.link.session;

import com.qnx.bbt.deploy.Messages;

/**
 * The texts of login failures. They come from the message bundle of the original tool, so that the
 * wording is the same as in bb-deploy.
 */
final class LoginMessages {

  private LoginMessages() {}

  static String passwordRejected(int failedAttempts, int remainingAttempts) {
    return remainingAttempts == 0
        ? Messages.getString("Deploy.AuthFailNoMoreAttempts", failedAttempts)
        : Messages.getString(
            "Deploy.AuthFailNAttempts", failedAttempts, remainingAttempts + failedAttempts);
  }

  static String invalidPassword() {
    return Messages.getString("Deploy.InvalidPassword");
  }

  static String clockOutOfSync() {
    return Messages.getString("Deploy.ClockOutOfSync");
  }

  static String systemError() {
    return Messages.getString("Deploy.SystemError");
  }
}
