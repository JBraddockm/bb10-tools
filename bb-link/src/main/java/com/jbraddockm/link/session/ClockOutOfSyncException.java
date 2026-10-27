package com.jbraddockm.link.session;

import java.io.Serial;

/** The device issued a cookie that could not be used, which happens when the clocks differ. */
final class ClockOutOfSyncException extends LoginException {

  @Serial private static final long serialVersionUID = 1L;

  ClockOutOfSyncException() {
    super(LoginMessages.clockOutOfSync());
  }
}
