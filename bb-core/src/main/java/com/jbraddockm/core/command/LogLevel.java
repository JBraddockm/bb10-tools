package com.jbraddockm.core.command;

import java.util.Locale;

/** Severity of a message produced while a command runs. */
public enum LogLevel {

  /** Diagnostic detail. */
  DEBUG,

  /** Normal progress information. */
  INFO,

  /** A problem that did not stop the command. */
  WARN;

  /**
   * Returns the lower-case name used in the wire protocol.
   *
   * @return {@code debug}, {@code info} or {@code warn}
   */
  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
