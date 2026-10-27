package com.jbraddockm.link;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.link.daemon.FixtureReplay;
import com.jbraddockm.link.daemon.LinkService;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.trace.TraceRecorder;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

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
  private static final String USAGE =
      "usage: bb-link -daemon [-trace <directory>] | -parse <command>";
  private static final String DAEMON_USAGE = "usage: bb-link -daemon [-trace <directory>]";

  private Launcher() {}

  /** What the command line asks for. */
  sealed interface Invocation {

    /**
     * Serve the protocol on standard input and output.
     *
     * @param traceDirectory the folder in which to record every exchange with a device, when one
     *     was given
     */
    record Daemon(Optional<Path> traceDirectory) implements Invocation {}

    /**
     * Replay recorded answers through a command, without a device.
     *
     * @param command the name of the command
     */
    record Replay(String command) implements Invocation {}

    /**
     * Arguments that mean nothing: say how the tool is used.
     *
     * @param text what to say
     */
    record Usage(String text) implements Invocation {}
  }

  /**
   * Starts the requested mode.
   *
   * @param arguments the command-line arguments
   * @throws IOException if reading the daemon's input fails
   */
  public static void main(String[] arguments) throws IOException {
    System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
    System.setProperty("jdk.tls.client.protocols", "TLSv1");

    switch (interpret(arguments)) {
      case Invocation.Daemon(var traceDirectory) ->
          DaemonServer.run(new LinkService(observerFor(traceDirectory)));
      case Invocation.Replay(var command) -> System.exit(FixtureReplay.run(command));
      case Invocation.Usage(var text) -> {
        System.err.println(text);
        System.exit(USAGE_ERROR);
      }
    }
  }

  /**
   * Reads what the command line asks for, without doing any of it.
   *
   * @param arguments the command-line arguments
   * @return the mode, or the usage to say when the arguments mean nothing
   */
  static Invocation interpret(String[] arguments) {
    if (arguments.length > 0 && arguments[0].equals("-daemon")) {
      if (arguments.length == 3 && arguments[1].equals("-trace")) {
        return new Invocation.Daemon(Optional.of(Path.of(arguments[2])));
      }
      return arguments.length == 1
          ? new Invocation.Daemon(Optional.empty())
          : new Invocation.Usage(DAEMON_USAGE);
    }
    if (arguments.length == 2 && arguments[0].equals("-parse")) {
      return new Invocation.Replay(arguments[1]);
    }
    return new Invocation.Usage(USAGE);
  }

  private static ExchangeObserver observerFor(Optional<Path> traceDirectory) throws IOException {
    return traceDirectory.isPresent()
        ? new TraceRecorder(traceDirectory.get())
        : ExchangeObserver.NONE;
  }
}
