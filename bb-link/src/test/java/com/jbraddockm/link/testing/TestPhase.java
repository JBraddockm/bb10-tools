package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.ProgressPhase;

/**
 * A progress phase for tests that do not depend on which phase is used.
 *
 * @param wireName the name of the phase
 */
public record TestPhase(String wireName) implements ProgressPhase {

  /** The phase that tests use when it does not matter. */
  public static final TestPhase UPLOADING = new TestPhase("uploading");
}
