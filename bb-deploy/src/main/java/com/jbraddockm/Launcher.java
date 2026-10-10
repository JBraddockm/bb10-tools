package com.jbraddockm;

import com.jbraddockm.daemon.DaemonServer;
import com.jbraddockm.daemon.FixtureReplay;
import java.io.IOException;
import java.util.List;

/**
 * The application entry point. Without a special first argument it behaves exactly like the
 * original {@code bb-deploy}; two extra modes are added:
 *
 * <ul>
 *   <li>{@code -daemon} serves the JSON protocol on standard input and output (see {@link
 *       DaemonServer});
 *   <li>{@code -parse <command>} replays recorded output from standard input (see {@link
 *       FixtureReplay}).
 * </ul>
 */
public final class Launcher {

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

    if (List.of(arguments).contains("-gui")) {
      System.err.println("Error: -gui is not supported in the native build.");
      System.exit(1);
    }

    if (arguments.length > 0 && arguments[0].equals("-daemon")) {
      DaemonServer.run();
    } else if (arguments.length == 2 && arguments[0].equals("-parse")) {
      System.exit(FixtureReplay.run(arguments[1]));
    } else {
      com.qnx.bbt.deploy.Deploy.main(arguments);
    }
  }
}
