package com.jbraddockm.tunnel;

import com.jbraddockm.tunnel.broker.Tunneld;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * The bb-tunnel command line. Two modes:
 *
 * <ul>
 *   <li>{@code bb-tunnel serve [socket]} — run the tunnel-broker daemon ({@link Tunneld}).
 *   <li>{@code bb-tunnel [device-ip …]} — the demo: open a tunnel to each device through {@link
 *       TunnelManager}, print the coordinates a consumer connects with, and park until Ctrl-C.
 * </ul>
 *
 * <p>Requires {@code sudo} (the utun is privileged). The logic lives in {@link TunnelManager} and
 * {@link Tunneld}; this class only parses arguments and prints.
 */
public final class Cli {

  private Cli() {}

  static void main(String[] args) throws Throwable {
    if (args.length > 0 && args[0].equals("serve")) {
      Tunneld.run(args.length > 1 ? Path.of(args[1]) : Path.of(Tunneld.DEFAULT_SOCKET));
      return;
    }
    String[] hosts = args.length > 0 ? args : new String[] {"192.168.1.118"};
    TunnelManager manager = new TunnelManager();
    List<TunnelManager.Lease> leases = new ArrayList<>();
    try {
      for (String host : hosts) {
        TunnelManager.Lease lease;
        try {
          // The first device that opens takes the stable pair; the rest get pooled pairs.
          lease = manager.acquire(host, leases.isEmpty());
        } catch (Throwable failure) {
          IO.println();
          IO.println("could not open a tunnel to " + host + ": " + rootCause(failure));
          continue; // a device we cannot reach (e.g. unpaired, no cert) does not stop the others
        }
        leases.add(lease);
        IO.println();
        IO.println("tunnel up to " + host);
        IO.println("  device ULA : " + lease.deviceUla());
        IO.println(
            "  SMB host   : " + lease.deviceIpv4() + "  (smb://" + lease.deviceIpv4() + "/)");
        IO.println(
            "  bb-link    : bblink connect --host "
                + lease.deviceUla()
                + " --smb-host "
                + lease.deviceIpv4());
      }
      if (leases.isEmpty()) {
        IO.println();
        IO.println("no tunnel could be opened.");
        return;
      }
      IO.println();
      IO.println("Reach a device's Wi-Fi services over its ULA: https://[<ULA>]:<port>/");
      IO.println("(e.g. :8443 CGIs, :8080 WebDAV, :2708 Blend). Ctrl-C to stop.");
      new CountDownLatch(1).await(); // park; the daemon threads carry the tunnels until Ctrl-C
    } finally {
      for (int i = leases.size() - 1; i >= 0; i--) {
        try {
          leases.get(i).close();
        } catch (Throwable closing) {
          IO.println("error closing a tunnel: " + rootCause(closing));
        }
      }
    }
  }

  /** The short root-cause text of a failure, for a one-line message rather than a stack trace. */
  private static String rootCause(Throwable error) {
    var cause = error;
    while (cause.getCause() != null && cause.getCause() != cause) {
      cause = cause.getCause();
    }
    var text = String.valueOf(cause);
    if (text.contains("unknown_ca")) {
      text +=
          "  (the device rejected the tunnel certificate — is it paired and are its certs in"
              + " place?)";
    }
    return text;
  }
}
