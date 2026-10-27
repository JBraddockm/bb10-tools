package com.jbraddockm.link.transport.session;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link BrokerTunnelConnector}: the concrete {@link TunnelConnector} that drives the
 * bb-core broker client. It runs against a stand-in broker on a Unix-domain socket, so the real
 * {@code TunnelBrokerClient} wiring is exercised end to end. The broker's own tunnel service is
 * covered in bb-tunnel, which bb-link does not depend on, so that half is faked here.
 */
@DisplayName("BrokerTunnelConnector: the broker-backed Wi-Fi tunnel")
class BrokerTunnelConnectorTest {

  private FakeBroker broker;

  @BeforeEach
  void startBroker() throws IOException {
    broker = new FakeBroker();
  }

  @AfterEach
  void stopBroker() throws IOException {
    broker.close();
  }

  private BrokerTunnelConnector connector() {
    return new BrokerTunnelConnector(
        broker.socket,
        "192.168.1.118",
        true,
        "bb-link",
        Path.of("/devices/2C1E7583/tunnel_cert.pem"),
        Path.of("/devices/2C1E7583/tunnel_key.pem"));
  }

  /** A bounded wait for an asynchronously delivered broker event (the drop arrives off-thread). */
  private static void awaitTrue(BooleanSupplier condition) throws InterruptedException {
    for (var attempt = 0; attempt < 200; attempt++) {
      if (condition.getAsBoolean()) {
        return;
      }
      Thread.sleep(10);
    }
    throw new AssertionError("the condition never became true");
  }

  @Nested
  @DisplayName("a tunnel it brings up")
  class BringUp {

    @Test
    @DisplayName("binds over the lease's ULA and reports the inner IPv4 as the SMB host")
    void bindsOverTheLease() throws IOException {
      try (var binding = connector().connect()) {
        assertThat(binding.ula()).isEqualTo("fd00::dev");
        assertThat(binding.smbHost()).isEqualTo("169.254.1.1");
        assertThat(binding.isDown()).isFalse();
      }
    }

    @Test
    @DisplayName("acquires for the configured host and hands the identity paths to the daemon")
    void passesWhatTheDaemonNeeds() throws IOException {
      try (var binding = connector().connect()) {
        assertThat(broker.acquires)
            .singleElement()
            .satisfies(
                params -> {
                  assertThat(params.get("host")).isEqualTo("192.168.1.118");
                  assertThat(params.get("stable")).isEqualTo(Boolean.TRUE);
                  assertThat(params.get("consumer")).isEqualTo("bb-link");
                  assertThat(params.get("certificate"))
                      .isEqualTo("/devices/2C1E7583/tunnel_cert.pem");
                  assertThat(params.get("key")).isEqualTo("/devices/2C1E7583/tunnel_key.pem");
                });
      }
    }
  }

  @Nested
  @DisplayName("when the broker drops the tunnel")
  class Dropped {

    @Test
    @DisplayName("a dropped event flips the binding down")
    void dropFlipsDown() throws Exception {
      try (var binding = connector().connect()) {
        assertThat(binding.isDown()).isFalse();

        broker.pushDropped("192.168.1.118");

        awaitTrue(binding::isDown);
      }
    }
  }

  @Nested
  @DisplayName("releasing the hold")
  class Release {

    @Test
    @DisplayName("close releases the lease by its token")
    void closeReleasesByToken() throws IOException {
      var binding = connector().connect();

      binding.close();

      // release() blocks for the broker's reply, so the record is complete once close returns.
      assertThat(broker.releases).containsExactly("T1");
    }

    @Test
    @DisplayName("a release the broker refuses is swallowed — close still completes")
    void closeSurvivesAFailedRelease() throws IOException {
      broker.rejectRelease = true;
      var binding = connector().connect();

      binding.close(); // must not throw; the daemon reclaims the lease on disconnect anyway

      assertThat(broker.releases).containsExactly("T1");
    }
  }

  @Nested
  @DisplayName("when the broker refuses")
  class Refused {

    @Test
    @DisplayName("a refused acquire surfaces the broker's reason and does not leak the connection")
    void refusedAcquireFails() {
      broker.reject = true;

      assertThatThrownBy(() -> connector().connect())
          .isInstanceOf(IOException.class)
          .hasMessageContaining("no such device");
    }
  }

  /**
   * A stand-in tunnel broker on a Unix-domain socket. It answers the client's {@code acquire} and
   * {@code release} line protocol with canned coordinates, records what it was asked, and can push
   * a spontaneous {@code dropped} event to the connected client.
   */
  private static final class FakeBroker implements AutoCloseable {

    private final Path socket;
    private final ServerSocketChannel listener;
    private final Thread serving;
    private final List<Map<String, Object>> acquires = new CopyOnWriteArrayList<>();
    private final List<String> releases = new CopyOnWriteArrayList<>();
    private volatile boolean reject;
    private volatile boolean rejectRelease;
    private volatile PrintStream toClient;

    FakeBroker() throws IOException {
      // A short path: macOS caps a Unix-domain socket path near 104 bytes.
      socket = Path.of("/tmp", "bb-link-broker-test-" + System.nanoTime() + ".sock");
      listener = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      listener.bind(UnixDomainSocketAddress.of(socket));
      serving = Thread.ofPlatform().daemon().name("fake-broker").start(this::serve);
    }

    void pushDropped(String host) {
      var out = toClient;
      if (out == null) {
        throw new IllegalStateException("no client has connected yet");
      }
      send(out, Json.object("event", "dropped", "host", host, "cause", "link lost"));
    }

    @Override
    public void close() throws IOException {
      listener.close();
      serving.interrupt();
      Files.deleteIfExists(socket);
    }

    private void serve() {
      try (var channel = listener.accept()) {
        var reader =
            new BufferedReader(new InputStreamReader(Channels.newInputStream(channel), UTF_8));
        var out = new PrintStream(Channels.newOutputStream(channel), false, UTF_8);
        toClient = out;
        String line;
        while ((line = reader.readLine()) != null) {
          if (!line.isBlank() && Json.parse(line) instanceof Map<?, ?> message) {
            handle(cast(message), out);
          }
        }
      } catch (IOException closed) {
        // the listener or the connection was closed; nothing left to serve
      }
    }

    private void handle(Map<String, Object> message, PrintStream out) {
      var id = message.get("id");
      switch (String.valueOf(message.get("op"))) {
        case "acquire" -> {
          acquires.add(cast(message.get("params")));
          send(
              out,
              reject
                  ? Json.object(
                      "id",
                      id,
                      "ok",
                      false,
                      "error",
                      Json.object("code", "no_device", "message", "no such device"))
                  : Json.object(
                      "id",
                      id,
                      "ok",
                      true,
                      "token",
                      "T1",
                      "ula",
                      "fd00::dev",
                      "innerIpv4",
                      "169.254.1.1",
                      "refs",
                      1));
        }
        case "release" -> {
          releases.add(String.valueOf(cast(message.get("params")).get("token")));
          send(
              out,
              rejectRelease
                  ? Json.object(
                      "id",
                      id,
                      "ok",
                      false,
                      "error",
                      Json.object("code", "unknown_token", "message", "no such lease"))
                  : Json.object("id", id, "ok", true));
        }
        default -> send(out, Json.object("id", id, "ok", true));
      }
    }

    private void send(PrintStream out, Map<String, Object> message) {
      synchronized (out) {
        out.print(Json.write(message) + "\n");
        out.flush();
      }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object map) {
      return map instanceof Map<?, ?> typed ? (Map<String, Object>) typed : Map.of();
    }
  }
}
