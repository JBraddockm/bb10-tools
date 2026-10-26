package com.jbraddockm.tunnel.broker;

import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.tunnel.TunnelManager;
import java.io.IOException;
import java.nio.file.Path;

/**
 * The tunnel-broker daemon entry point: wires a real {@link TunnelManager} into a {@link
 * TunnelBroker} and serves it on a Unix-domain socket until the process is stopped.
 *
 * <p>Run it from the tunnel CLI as {@code sudo bb-tunnel serve [socket-path]} — {@code sudo} is
 * required because bringing up a tunnel creates a privileged {@code utun}. A launchd root job and a
 * fixed socket path come later (see {@code analysis/TUNNEL-BROKER-DAEMON-PLAN.md} §9.2).
 */
public final class Tunneld {

  /** The default socket path when none is given; overridable on the command line. */
  public static final String DEFAULT_SOCKET = TunnelBrokerClient.DEFAULT_SOCKET.toString();

  private static final String VERSION = "1.0";

  private Tunneld() {}

  /** Serves the broker on {@code socketPath}. Blocks until the process is interrupted or killed. */
  public static void run(Path socketPath) throws IOException {
    var broker = new TunnelBroker(new TunnelManager());
    try (var server = new BrokerServer(broker, socketPath, VERSION)) {
      // Stop the accept loop on SIGTERM/SIGINT so the socket file is removed on a clean signal,
      // not only on a graceful close(). A stale file from a hard kill is cleared at next startup.
      Runtime.getRuntime().addShutdownHook(new Thread(server::close, "bb-tunneld-shutdown"));
      System.err.println("bb-tunneld: listening on " + socketPath);
      server.serve();
    }
  }
}
