package com.jbraddockm.core.command;

/** Receives what a running command reports before it finishes. */
public interface CommandListener {

  /** A listener that ignores everything. */
  CommandListener SILENT =
      new CommandListener() {
        @Override
        public void onLog(LogLevel level, String message) {
          // intentionally ignored
        }

        @Override
        public void onProgress(Progress progress) {
          // intentionally ignored
        }
      };

  /**
   * Reports a message.
   *
   * @param level the severity
   * @param message the text
   */
  void onLog(LogLevel level, String message);

  /**
   * Reports progress.
   *
   * @param progress the report
   */
  void onProgress(Progress progress);
}
