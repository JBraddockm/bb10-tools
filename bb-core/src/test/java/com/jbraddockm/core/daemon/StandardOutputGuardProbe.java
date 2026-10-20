package com.jbraddockm.core.daemon;

/**
 * Child-process entry point for {@link StandardOutputGuardTest}: claims the protocol stream, then
 * writes to standard output and to the claimed stream.
 */
public final class StandardOutputGuardProbe {

  private StandardOutputGuardProbe() {}

  /**
   * Claims the stream, prints a stray line and writes a protocol line.
   *
   * @param arguments not used
   */
  public static void main(String[] arguments) {
    var protocol = StandardOutputGuard.claimProtocolStream();
    System.out.println("a stray line on standard output");
    protocol.println("a line of the protocol");
    protocol.flush();
  }
}
