package com.jbraddockm.link.transport.session;

import com.jbraddockm.core.tunnel.BrokerLease;
import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link TunnelConnector} backed by the tunnel-broker daemon: it connects to the broker's
 * Unix-domain socket, acquires a shared tunnel for the device's transport address (the Wi-Fi LAN
 * address the user gave), and returns a binding over the resulting ULA and inner IPv4. A broker
 * {@code dropped} event for the device flips the binding down. bb-link never opens a tunnel itself
 * — the daemon owns every tunnel's lifecycle, so bb-link and Blend are peers (see {@code
 * analysis/TUNNEL-BROKER-DAEMON-PLAN.md}).
 */
public final class BrokerTunnelConnector implements TunnelConnector {

  private final Path socket;
  private final String host;
  private final boolean stableSmb;
  private final String consumer;
  private final Path tunnelCert;
  private final Path tunnelKey;

  /**
   * @param socket the broker's Unix-domain socket
   * @param host the device's transport address to open the tunnel to (the Wi-Fi LAN address)
   * @param stableSmb whether to pin the constant {@code 169.254.1.1} SMB address
   * @param consumer a label for this consumer in the broker's status/logs
   * @param tunnelCert the device's tunnel certificate chain (leaf first) on disk, passed to the
   *     daemon so its binary need not embed any device's identity; {@code null} leaves the choice to
   *     the daemon's own resolution
   * @param tunnelKey the PKCS#8 private key for {@code tunnelCert}; {@code null} with the same effect
   */
  public BrokerTunnelConnector(
      Path socket,
      String host,
      boolean stableSmb,
      String consumer,
      Path tunnelCert,
      Path tunnelKey) {
    this.socket = socket;
    this.host = host;
    this.stableSmb = stableSmb;
    this.consumer = consumer;
    this.tunnelCert = tunnelCert;
    this.tunnelKey = tunnelKey;
  }

  @Override
  public TunnelBinding connect() throws IOException {
    var client = TunnelBrokerClient.connect(socket);
    try {
      var down = new AtomicBoolean();
      // Register the drop listener before acquiring, so a drop cannot slip through the gap.
      client.onEvent(
          event -> {
            if ("dropped".equals(event.type())) {
              down.set(true);
            }
          });
      var lease = client.acquire(host, stableSmb, consumer, tunnelCert, tunnelKey);
      return new Binding(client, lease, down);
    } catch (IOException | RuntimeException failure) {
      client.close();
      throw failure;
    }
  }

  private static final class Binding implements TunnelBinding {
    private final TunnelBrokerClient client;
    private final BrokerLease lease;
    private final AtomicBoolean down;

    Binding(TunnelBrokerClient client, BrokerLease lease, AtomicBoolean down) {
      this.client = client;
      this.lease = lease;
      this.down = down;
    }

    @Override
    public String ula() {
      return lease.ula();
    }

    @Override
    public String smbHost() {
      return lease.innerIpv4();
    }

    @Override
    public boolean isDown() {
      return down.get();
    }

    @Override
    public void close() {
      try {
        client.release(lease.token());
      } catch (IOException ignore) {
        // best effort: the daemon also reclaims the lease when this connection closes
      } finally {
        client.close();
      }
    }
  }
}
