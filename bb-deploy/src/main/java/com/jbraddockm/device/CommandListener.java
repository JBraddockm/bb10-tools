package com.jbraddockm.device;

import com.jbraddockm.output.LogLevel;
import com.jbraddockm.output.Progress;

/** Receives what a command reports while it is still running. All methods do nothing by default. */
public interface CommandListener {

  /** A listener that ignores everything. */
  CommandListener SILENT = new CommandListener() {};

  /**
   * Called for each message the tool prints for humans, and for notes the session adds, such as
   * renewing the login.
   *
   * @param level the severity
   * @param message the text
   */
  default void onLog(LogLevel level, String message) {}

  /**
   * Called for each progress report.
   *
   * @param progress the progress
   */
  default void onProgress(Progress progress) {}
}
