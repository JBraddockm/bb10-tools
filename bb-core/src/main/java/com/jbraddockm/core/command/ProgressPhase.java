package com.jbraddockm.core.command;

/**
 * The part of a command that a progress report refers to. Every tool defines its own phases as an
 * enum implementing this interface.
 */
public interface ProgressPhase {

  /**
   * Returns the identifier used in the wire protocol.
   *
   * @return the lower-case wire name
   */
  String wireName();
}
