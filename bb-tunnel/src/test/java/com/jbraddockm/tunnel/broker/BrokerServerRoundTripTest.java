package com.jbraddockm.tunnel.broker;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.tunnel.BrokerEvent;
import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.tunnel.InnerAddressPool;
import com.jbraddockm.tunnel.InnerIpv4;
import com.jbraddockm.tunnel.TunnelManager;
import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * End-to-end test: the bb-core {@link TunnelBrokerClient} driving {@link BrokerServer} over a UDS.
 */
@DisplayName("Broker round trip: client ↔ server over a Unix-domain socket")
class BrokerServerRoundTripTest {

  private final FakeOpener opener = new FakeOpener();
  private Path socket;
  private BrokerServer server;
  private Thread serving;

  private static BrokerEvent poll(LinkedBlockingQueue<BrokerEvent> events, String type)
      throws InterruptedException {
    for (int attempt = 0; attempt < 50; attempt++) {
      var event = events.poll(100, TimeUnit.MILLISECONDS);
      if (event != null && type.equals(event.type())) {
        return event;
      }
    }
    throw new IllegalStateException("no '" + type + "' event arrived");
  }

  @BeforeEach
  void startServer() throws Exception {
    // A short path: macOS caps a Unix-domain socket path near 104 bytes.
    socket = Path.of("/tmp", "bb-tunneld-test-" + System.nanoTime() + ".sock");
    var broker = new TunnelBroker(new TunnelManager(new InnerAddressPool(), opener));
    server = new BrokerServer(broker, socket, "test");
    serving =
        new Thread(
            () -> {
              try {
                server.serve();
              } catch (IOException ignore) {
                // the socket was closed by stopServer()
              }
            },
            "broker-test-server");
    serving.setDaemon(true);
    serving.start();
    awaitSocketReady();
  }

  @AfterEach
  void stopServer() throws Exception {
    server.close();
    serving.join(2000);
    Files.deleteIfExists(socket);
  }

  private void awaitSocketReady() throws Exception {
    for (int attempt = 0; attempt < 100; attempt++) {
      try (var probe = SocketChannel.open(StandardProtocolFamily.UNIX)) {
        probe.connect(UnixDomainSocketAddress.of(socket));
        return;
      } catch (IOException notYet) {
        Thread.sleep(10);
      }
    }
    throw new IllegalStateException("broker socket never became ready: " + socket);
  }

  @Test
  @DisplayName("acquire, status and release flow over the socket")
  void lifecycle() throws Exception {
    try (var client = TunnelBrokerClient.connect(socket, Duration.ofSeconds(5))) {
      var lease = client.acquire("192.168.1.118", true, "bb-link");
      assertThat(lease.ula()).isEqualTo("fd00::192.168.1.118");
      assertThat(lease.innerIpv4()).isEqualTo("169.254.1.1");
      assertThat(lease.token()).isNotBlank();
      assertThat(lease.refs()).isEqualTo(1);

      var tunnels = client.status();
      assertThat(tunnels)
          .singleElement()
          .satisfies(t -> assertThat(t.host()).isEqualTo("192.168.1.118"));

      client.release(lease.token());
      assertThat(client.status()).isEmpty();
    }
  }

  @Test
  @DisplayName("identify records the GUID and lets another client rejoin by it")
  void identifyAndRejoin() throws Exception {
    try (var one = TunnelBrokerClient.connect(socket, Duration.ofSeconds(5));
        var two = TunnelBrokerClient.connect(socket, Duration.ofSeconds(5))) {
      var events = new LinkedBlockingQueue<BrokerEvent>();
      one.onEvent(events::add);

      var first = one.acquire("192.168.1.118", false, "bb-link");
      one.identify("192.168.1.118", "GUID-1", "2C1E7583");

      var identity = poll(events, "identity");
      assertThat(identity.guid()).isEqualTo("GUID-1");

      var rejoined = two.acquireByGuid("GUID-1", "blend");
      assertThat(rejoined.ula()).isEqualTo(first.ula());
      assertThat(rejoined.refs()).isEqualTo(2);
    }
  }

  @Test
  @DisplayName("a spontaneous drop reaches the holding client as a dropped event")
  void dropEventDelivered() throws Exception {
    try (var client = TunnelBrokerClient.connect(socket, Duration.ofSeconds(5))) {
      var events = new LinkedBlockingQueue<BrokerEvent>();
      client.onEvent(events::add);
      client.acquire("192.168.1.118", false, "bb-link");

      opener.dropCallbacks.get("192.168.1.118").accept(new IllegalStateException("link lost"));

      var dropped = poll(events, "dropped");
      assertThat(dropped.host()).isEqualTo("192.168.1.118");
    }
  }

  private static final class FakeOpener implements TunnelManager.Opener {
    final Map<String, Consumer<Throwable>> dropCallbacks = new HashMap<>();

    @Override
    public TunnelManager.Connection open(String host, InnerIpv4 inner, Consumer<Throwable> onDrop) {
      dropCallbacks.put(host, onDrop);
      return new TunnelManager.Connection() {
        @Override
        public String deviceUla() {
          return "fd00::" + host;
        }

        @Override
        public void close() {}
      };
    }
  }
}
