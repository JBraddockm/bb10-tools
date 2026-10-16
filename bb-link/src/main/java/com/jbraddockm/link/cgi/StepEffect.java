package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import java.time.Duration;
import java.util.Objects;

/**
 * Something that is done before a request is sent: a conversation is a pure state machine, so it
 * asks for progress to be reported, a note to be logged or a pause to be made by returning them
 * with the request, and the runner does them.
 */
public sealed interface StepEffect {

  /**
   * Report how far the command is.
   *
   * @param progress the progress
   */
  record Report(Progress progress) implements StepEffect {

    /**
     * Validates the effect.
     *
     * @throws NullPointerException if the progress is {@code null}
     */
    public Report {
      Objects.requireNonNull(progress, "progress");
    }
  }

  /**
   * Write a line to the log of the command.
   *
   * @param level how serious it is
   * @param message what to say
   */
  record Note(LogLevel level, String message) implements StepEffect {

    /**
     * Validates the effect.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public Note {
      Objects.requireNonNull(level, "level");
      Objects.requireNonNull(message, "message");
    }
  }

  /**
   * Wait before the next request, for example between two polls.
   *
   * @param duration how long
   */
  record Pause(Duration duration) implements StepEffect {

    /**
     * Validates the effect.
     *
     * @throws NullPointerException if the duration is {@code null}
     * @throws IllegalArgumentException if it is negative
     */
    public Pause {
      Objects.requireNonNull(duration, "duration");
      if (duration.isNegative()) {
        throw new IllegalArgumentException("a pause cannot be negative");
      }
    }
  }
}
