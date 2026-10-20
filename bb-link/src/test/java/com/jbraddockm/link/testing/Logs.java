package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A {@link CommandListener} that records log messages and ignores progress. */
public final class Logs implements CommandListener {

  /** Creates an empty recorder. */
  public Logs() {}

  /**
   * One message that was logged.
   *
   * @param level how serious it was said to be
   * @param message what it said
   */
  public record Entry(LogLevel level, String message) {}

  private final List<Entry> entries = Collections.synchronizedList(new ArrayList<>());
  private final List<Progress> progress = Collections.synchronizedList(new ArrayList<>());

  @Override
  public void onLog(LogLevel level, String message) {
    entries.add(new Entry(level, message));
  }

  @Override
  public void onProgress(Progress report) {
    progress.add(report);
  }

  /**
   * Every progress that was reported, in the order it was reported.
   *
   * @return a copy
   */
  public List<Progress> progress() {
    synchronized (progress) {
      return List.copyOf(progress);
    }
  }

  /**
   * Every message, in the order it was logged.
   *
   * @return a copy
   */
  public List<Entry> entries() {
    synchronized (entries) {
      return List.copyOf(entries);
    }
  }

  /**
   * The messages of one level.
   *
   * @param level the level
   * @return the texts, in the order they were logged
   */
  public List<String> messages(LogLevel level) {
    return entries().stream().filter(entry -> entry.level() == level).map(Entry::message).toList();
  }
}
