package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.transport.session.ExchangeObserver;
import com.jbraddockm.link.transport.session.TransportScheme;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@code tunnel} opt-in of {@link LinkService#open}: a Wi-Fi LAN connect with {@code
 * tunnel} is given a broker tunnel to bring up, a USB connect is not, and a bring-up failure is
 * surfaced as a rejected connect. The broker socket points nowhere, so the connector fails fast
 * without a daemon — the broker round trip itself is covered in bb-tunnel.
 */
@DisplayName("LinkService: the tunnel opt-in of connect")
class LinkServiceTunnelTest {

  private final LinkService service =
      new LinkService(
          TransportScheme.HTTP,
          _ -> {},
          ExchangeObserver.NONE,
          new Pairing(),
          new CertificateRetrieval());

  private static String noBrokerSocket() {
    return "/tmp/bb-link-no-broker-" + System.nanoTime() + ".sock";
  }

  private static RequestParams connect(String host, boolean tunnel, String brokerSocket) {
    return new RequestParams(
        Json.object(
            "host", host,
            "authentication", Json.object("type", "none"),
            "tunnel", tunnel,
            "brokerSocket", brokerSocket,
            "deviceInfo", false));
  }

  @Test
  @DisplayName("a Wi-Fi LAN connect with tunnel is given a tunnel to bring up")
  void lanConnectIsTunnelled() {
    var session = service.open(connect("192.168.1.50", true, noBrokerSocket()));

    // The connector is attached, so resolving it reaches the (absent) broker and fails.
    assertThatThrownBy(() -> session.establishTunnel(CommandListener.SILENT))
        .isInstanceOf(IOException.class);
    session.close();
  }

  @Test
  @DisplayName("a USB connect is never tunnelled, even with tunnel set")
  void usbConnectIsNotTunnelled() {
    var session = service.open(connect("169.254.0.2", true, noBrokerSocket()));

    // No connector was attached for USB, so resolving is a no-op and never touches the broker.
    assertThatCode(() -> session.establishTunnel(CommandListener.SILENT))
        .doesNotThrowAnyException();
    session.close();
  }

  @Test
  @DisplayName("a tunnel that cannot be brought up makes the connect fail, not hang")
  void bringUpFailureRejectsTheConnect() {
    var params = connect("192.168.1.50", true, noBrokerSocket());
    var session = service.open(params);
    var reply =
        Reply.standalone(
            new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8), 1);

    var establishment = service.establish(session, params, reply);

    assertThat(establishment).isInstanceOf(Establishment.Rejected.class);
    assertThat(((Establishment.Rejected) establishment).error().message())
        .contains("could not be brought up");
    session.close();
  }
}
