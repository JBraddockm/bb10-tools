package com.jbraddockm.core.testing;

import com.jbraddockm.core.command.ProgressPhase;
import java.util.Locale;

/** A progress phase for tests that do not depend on which phase is used. */
public enum TestPhase implements ProgressPhase {
  /** The phase that tests use when the phase does not matter. */
  UPLOADING;

  @Override
  public String wireName() {
    return name().toLowerCase(Locale.ROOT);
  }
}
