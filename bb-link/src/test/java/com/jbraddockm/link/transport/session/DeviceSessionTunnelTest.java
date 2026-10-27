package com.jbraddockm.link.transport.session;

import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.endpoint.actions.GetPin;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.tunnel.TunnelBinding;
import com.jbraddockm.link.transport.tunnel.TunnelConnector;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DeviceSession}'s Wi-Fi tunnel integration: a configured session is redirected to
 * connect over the tunnel's ULA, reports the tunnel's SMB host, surfaces a drop, and releases the
 * lease on close. Uses a fake {@link TunnelConnector} — the real broker round trip is covered in
 * bb-tunnel, which bb-link does not depend on.
 */
@DisplayName("DeviceSession: reaching the device over a broker tunnel")
class DeviceSessionTunnelTest {

  private static DeviceSession tunnelled(
      String configuredHost, int port, TunnelConnector connector) {
    var session =
        DeviceSession.forTarget(
            new DeviceTarget(
                configuredHost, port, TransportScheme.HTTP, new Authentication.Password("secret")),
            ExchangeObserver.NONE,
            null);
    session.useTunnel(connector);
    return session;
  }

  /** A tunnel hold with canned coordinates that records its release and can be marked down. */
  private static final class FakeBinding implements TunnelBinding {
    final String ula;
    final String smbHost;
    boolean down;
    int closes;

    FakeBinding(String ula, String smbHost) {
      this.ula = ula;
      this.smbHost = smbHost;
    }

    @Override
    public String ula() {
      return ula;
    }

    @Override
    public String smbHost() {
      return smbHost;
    }

    @Override
    public boolean isDown() {
      return down;
    }

    @Override
    public void close() {
      closes++;
    }
  }

  /** A connector that hands back a given binding and counts how often it is resolved. */
  private static final class FakeConnector implements TunnelConnector {
    final TunnelBinding binding;
    int connects;

    FakeConnector(TunnelBinding binding) {
      this.binding = binding;
    }

    @Override
    public TunnelBinding connect() {
      connects++;
      return binding;
    }
  }

  @Nested
  @DisplayName("redirecting the transport")
  class Redirect {

    @Test
    @DisplayName("connects over the tunnel ULA, not the address the user gave")
    void connectsOverTheUla() throws IOException {
      var device = new FakeDevice("secret");
      try {
        // The configured host is an unreachable LAN address; only the redirect to the binding's ULA
        // (the loopback the fake device listens on) lets the command reach the device.
        var binding = new FakeBinding("127.0.0.1", "169.254.7.1");
        var session = tunnelled("10.255.255.1", device.port(), new FakeConnector(binding));

        session.establishTunnel(CommandListener.SILENT);
        succeeded(session.execute(new GetPin(), CommandListener.SILENT));

        assertThat(session.target().host()).isEqualTo("10.255.255.1"); // the ULA stays hidden
        session.close();
      } finally {
        device.close();
      }
    }

    @Test
    @DisplayName("is resolved once, however many times establishTunnel is called")
    void resolvedOnce() throws IOException {
      var connector = new FakeConnector(new FakeBinding("fd00::1", "169.254.2.1"));
      var session = tunnelled("10.0.0.5", 8443, connector);

      session.establishTunnel(CommandListener.SILENT);
      session.establishTunnel(CommandListener.SILENT);

      assertThat(connector.connects).isEqualTo(1);
      session.close();
    }

    @Test
    @DisplayName("a session with no tunnel configured is left untouched")
    void noTunnelIsANoOp() throws IOException {
      var session =
          DeviceSession.forTarget(
              new DeviceTarget(
                  "10.0.0.5", 8443, TransportScheme.HTTP, new Authentication.Password("secret")),
              ExchangeObserver.NONE,
              null);

      session.establishTunnel(CommandListener.SILENT); // must not throw

      assertThat(session.connectionPath()).isEqualTo(ConnectionPath.LAN);
      session.close();
    }
  }

  @Nested
  @DisplayName("tunnel coordinates")
  class Coordinates {

    @Test
    @DisplayName(
        "the effective path becomes the tunnel and the SMB host is the tunnel's inner IPv4")
    void pathAndSmbHostFollowTheTunnel() throws IOException {
      var session =
          tunnelled(
              "10.0.0.5", 8443, new FakeConnector(new FakeBinding("fd00::abcd", "169.254.9.1")));

      session.establishTunnel(CommandListener.SILENT);

      assertThat(session.connectionPath()).isEqualTo(ConnectionPath.TUNNEL);
      assertThat(session.smbHost()).isEqualTo("169.254.9.1");
      session.close();
    }
  }

  @Nested
  @DisplayName("when the tunnel goes wrong")
  class GoingWrong {

    @Test
    @DisplayName("a dropped tunnel fails commands with a clear message instead of hanging")
    void dropIsSurfaced() throws IOException {
      var binding = new FakeBinding("127.0.0.1", "169.254.7.1");
      var session = tunnelled("10.0.0.5", 8443, new FakeConnector(binding));
      session.establishTunnel(CommandListener.SILENT);
      binding.down = true;

      var outcome = session.execute(new GetPin(), CommandListener.SILENT);

      assertThat(outcome).isInstanceOf(CommandOutcome.Failed.class);
      assertThat(((CommandOutcome.Failed<?>) outcome).error().message()).contains("dropped");
      session.close();
    }

    @Test
    @DisplayName("a connector that cannot bring the tunnel up propagates the failure")
    void bringUpFailurePropagates() {
      TunnelConnector failing =
          () -> {
            throw new IOException("the broker is not running");
          };
      var session = tunnelled("10.0.0.5", 8443, failing);

      assertThatThrownBy(() -> session.establishTunnel(CommandListener.SILENT))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("broker is not running");
      session.close();
    }
  }

  @Nested
  @DisplayName("releasing the lease")
  class Releasing {

    @Test
    @DisplayName("close releases the tunnel hold exactly once")
    void closeReleasesOnce() throws IOException {
      var binding = new FakeBinding("fd00::1", "169.254.2.1");
      var session = tunnelled("10.0.0.5", 8443, new FakeConnector(binding));
      session.establishTunnel(CommandListener.SILENT);

      session.close();
      session.close();

      assertThat(binding.closes).isEqualTo(1);
    }
  }
}
