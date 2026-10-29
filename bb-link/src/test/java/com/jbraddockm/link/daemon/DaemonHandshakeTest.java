package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.testing.JsonObjects.array;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.endpoint.discovery.ConnectionKind;
import com.jbraddockm.link.endpoint.discovery.DiscoveredDevice;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Protocol-level tests for the daemon greeting, device discovery, unknown sessions and end of
 * input.
 */
@DisplayName("The daemon: what a client meets when it starts")
class DaemonHandshakeTest {

  private static final List<DiscoveredDevice> FOUND =
      List.of(
          new DiscoveredDevice("blackberry-0000.lan", "192.168.1.82", ConnectionKind.WIFI, null),
          new DiscoveredDevice(
              "BlackBerry 10 10.3.3.2205", "169.254.0.1", ConnectionKind.USB, "10.3.3.2205"));

  private DaemonRun daemon;

  @BeforeEach
  void startTheDaemon() throws Exception {
    var service =
        new LinkService(
            TransportScheme.HTTP,
            FOUND::forEach,
            ExchangeObserver.NONE,
            new Pairing(),
            new CertificateRetrieval());
    daemon = new DaemonRun(service);
  }

  @AfterEach
  void stopTheDaemon() throws Exception {
    daemon.close();
  }

  @Test
  @DisplayName(
      "greets with the name of the tool and the methods that it answers to: the three of the"
          + " protocol, then the twenty-eight of the tool, in order")
  void greets() throws Exception {
    var hello = daemon.hello();

    assertThat(hello.get("tool")).isEqualTo("bb-link");
    assertThat(hello.get("methods"))
        .isEqualTo(
            Resources.text(DaemonHandshakeTest.class, "hello--methods.txt").lines().toList());
  }

  @Test
  @DisplayName(
      "discovers devices without a session, one event for each as it is found, and lists them all"
          + " in its result")
  void discoversDevices() throws Exception {
    daemon.hello();

    daemon.send(1, "request--list-devices.json");

    assertThat(daemon.done(1).get("ok")).isEqualTo(true);
    assertThat(daemon.events(1, "device")).hasSize(2);
    assertThat(daemon.events(1, "device").getFirst().get("connection"))
        .as("the spelling of the kind of connection")
        .isEqualTo("WiFi");
    var devices = array(daemon.data(1).get("devices"));
    assertThat(devices).hasSize(2);
    assertThat(object(devices.getFirst()))
        .as("an unknown version is left out")
        .doesNotContainKey("version");
    assertThat(object(devices.getLast())).containsEntry("version", "10.3.3.2205");
  }

  @Test
  @DisplayName("answers UNKNOWN_SESSION to a command for a session that was never opened")
  void anUnknownSession() throws Exception {
    daemon.hello();

    daemon.send(4, "request--unknown-session.json");

    assertThat(errorCode(daemon.done(4))).isEqualTo("UNKNOWN_SESSION");
  }

  @Test
  @DisplayName("stops when its input ends, as when the client that started it goes away")
  void stopsAtTheEndOfItsInput() throws Exception {
    daemon.hello();

    daemon.endInput();

    assertThat(daemon.stopped()).isTrue();
  }
}
