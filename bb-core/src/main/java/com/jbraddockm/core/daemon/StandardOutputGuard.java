package com.jbraddockm.core.daemon;

import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * Reserves the real standard output for the protocol. Anything else the process prints through
 * {@link System#out}, for example output of a library, goes to standard error instead and cannot
 * corrupt the stream of events.
 */
public final class StandardOutputGuard {

  private StandardOutputGuard() {}

  /**
   * Takes ownership of the real standard output.
   *
   * @return the stream that carries the protocol; {@link System#out} is redirected to standard
   *     error afterwards
   */
  public static PrintStream claimProtocolStream() {
    var protocol =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    System.setOut(System.err);
    return protocol;
  }
}
