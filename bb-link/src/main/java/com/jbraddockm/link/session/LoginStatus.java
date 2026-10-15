package com.jbraddockm.link.session;

import java.util.Arrays;
import java.util.Optional;

/** The status values of the login interface. */
enum LoginStatus {
  PASSWORD_CHALLENGE("PasswdChallenge"),
  SUCCESS("Success"),
  DENIED("Denied"),
  ERROR("Error");

  private final String wireName;

  LoginStatus(String wireName) {
    this.wireName = wireName;
  }

  static Optional<LoginStatus> of(String text) {
    return Arrays.stream(values())
        .filter(status -> status.wireName.equalsIgnoreCase(text))
        .findFirst();
  }
}
