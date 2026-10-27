package com.jbraddockm.link.transport.session;

/**
 * The elements of a login answer. Old devices answer inside a {@code RIM_tablet_response} root and
 * write all names except {@code Status} in lower case.
 */
enum LoginKey {
  STATUS("Status", "Status"),
  RETRIES_REMAINING("RetriesRemaining", "retriesremaining"),
  FAILED_ATTEMPTS("FailedAttempts", "failedattempts"),
  CHALLENGE("Challenge", "challenge"),
  ALGORITHM("Algorithm", "algorithm"),
  ITERATION_COUNT("ICount", "icount"),
  SALT("Salt", "salt");

  private final String currentName;
  private final String legacyName;

  LoginKey(String currentName, String legacyName) {
    this.currentName = currentName;
    this.legacyName = legacyName;
  }

  String elementName(boolean legacyRoot) {
    return legacyRoot ? legacyName : currentName;
  }
}
