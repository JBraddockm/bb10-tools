package com.jbraddockm.output;

import java.util.Locale;

/**
 * A progress report from a running command.
 *
 * @param percent the completed share, from 0 to 100; the device reports it at irregular steps and
 *     may repeat a value
 * @param phase the part of the command that is progressing
 */
public record Progress(int percent, Phase phase) {

  /** The part of a command a progress report refers to. */
  public enum Phase {

    /** The device is installing an uploaded package. */
    INSTALL,

    /** Files are being synchronised with the device. */
    SYNC;

    /**
     * Returns the lower-case name used in the wire protocol.
     *
     * @return {@code install} or {@code sync}
     */
    public String wireName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
