package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A listener that keeps the warnings that a command told, and ignores the rest. */
public final class Warnings implements CommandListener {

  private final List<String> warnings = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void onLog(LogLevel level, String message) {
    if (level == LogLevel.WARN) {
      warnings.add(message);
    }
  }

  @Override
  public void onProgress(Progress progress) {
    // The progress of a command is of no interest to those who count its warnings.
  }

  /**
   * The warnings that were told.
   *
   * @return a copy, in the order they came
   */
  public List<String> warnings() {
    synchronized (warnings) {
      return List.copyOf(warnings);
    }
  }
}
