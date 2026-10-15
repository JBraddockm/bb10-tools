package com.jbraddockm.link;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.link.daemon.FixtureReplay;
import com.jbraddockm.link.daemon.LinkService;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.trace.TraceRecorder;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The application entry point.
 *
 * <ul>
 *   <li>{@code -daemon [-trace <directory>]} serves the JSON protocol on standard input and output.
 *       With {@code -trace}, every exchange with a device is recorded, scrubbed, in that directory.
 *   <li>{@code -parse <command>} replays recorded answers from standard input through the command's
 *       conversation, without a device.
 * </ul>
 */
public final class Launcher {

  private static final int USAGE_ERROR = 2;

  private Launcher() {}

  /**
   * Starts the requested mode.
   *
   * @param arguments the command-line arguments
   * @throws IOException if reading the daemon's input fails
   */
  public static void main(String[] arguments) throws IOException {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    System.setProperty("jdk.tls.client.protocols", "TLSv1");

    if (arguments.length > 0 && arguments[0].equals("-daemon")) {
      DaemonServer.run(new LinkService(observerFor(arguments)));
    } else if (arguments.length == 2 && arguments[0].equals("-parse")) {
      System.exit(FixtureReplay.run(arguments[1]));
    } else {
      System.err.println("usage: bb-link -daemon [-trace <directory>] | -parse <command>");
      System.exit(USAGE_ERROR);
    }
  }

  private static ExchangeObserver observerFor(String[] arguments) throws IOException {
    if (arguments.length == 3 && arguments[1].equals("-trace")) {
      return new TraceRecorder(Path.of(arguments[2]));
    }
    if (arguments.length != 1) {
      System.err.println("usage: bb-link -daemon [-trace <directory>]");
      System.exit(USAGE_ERROR);
    }
    return ExchangeObserver.NONE;
  }
}
