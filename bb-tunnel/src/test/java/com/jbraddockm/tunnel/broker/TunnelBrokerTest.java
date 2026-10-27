package com.jbraddockm.tunnel.broker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.tunnel.Tunnels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link TunnelBroker}: leases, identity and events over a faked {@link Tunnels}. */
@DisplayName("TunnelBroker: leases, identity and events over the tunnel service")
class TunnelBrokerTest {

  private final FakeTunnels tunnels = new FakeTunnels();
  private final TunnelBroker broker = new TunnelBroker(tunnels);

  private static RequestParams params(Object... keysAndValues) {
    return new RequestParams(Json.object(keysAndValues));
  }

  /**
   * A {@link Tunnels} stand-in: one shared, ref-counted entry per host with canned coordinates, and
   * a hook to fire a spontaneous drop. The real pool and ref-count are covered by {@code
   * TunnelManagerTest}; here it only has to behave like the service the broker talks to.
   */
  private static final class FakeTunnels implements Tunnels {
    private record Info(String ula, String innerIpv4) {}

    private final Map<String, Info> up = new HashMap<>();
    private final Map<String, Integer> refs = new HashMap<>();
    private BiConsumer<String, Throwable> onDrop;

    @Override
    public synchronized Lease acquire(String host, boolean stable) {
      var info =
          up.computeIfAbsent(
              host, h -> new Info("fd00::" + h, stable ? "169.254.1.1" : "169.254.9.1"));
      refs.merge(host, 1, Integer::sum);
      return new FakeLease(host, info);
    }

    @Override
    public synchronized boolean isOpen(String host) {
      return up.containsKey(host);
    }

    @Override
    public void onDrop(BiConsumer<String, Throwable> listener) {
      this.onDrop = listener;
    }

    /** Simulates a spontaneous drop of {@code host}, as a real tunnel failing would. */
    void drop(String host, Throwable cause) {
      synchronized (this) {
        up.remove(host);
        refs.remove(host);
      }
      if (onDrop != null) {
        onDrop.accept(host, cause);
      }
    }

    private synchronized void release(String host) {
      if (refs.merge(host, -1, Integer::sum) <= 0) {
        refs.remove(host);
        up.remove(host);
      }
    }

    private final class FakeLease implements Lease {
      private final String host;
      private final Info info;
      private boolean closed;

      FakeLease(String host, Info info) {
        this.host = host;
        this.info = info;
      }

      @Override
      public String deviceUla() {
        return info.ula();
      }

      @Override
      public String deviceIpv4() {
        return info.innerIpv4();
      }

      @Override
      public String host() {
        return host;
      }

      @Override
      public void close() {
        if (!closed) {
          closed = true;
          release(host);
        }
      }
    }
  }

  /** Collects the events written to one connection. */
  private static final class Events implements TunnelBroker.EventSink {
    final List<Map<String, Object>> received = new ArrayList<>();

    @Override
    public void event(Map<String, Object> event) {
      received.add(event);
    }
  }

  @Nested
  @DisplayName("acquire / status / release")
  class Lifecycle {

    @Test
    @DisplayName("acquire reports the coordinates and token; status lists the tunnel")
    void acquireAndStatus() throws Exception {
      broker.connected("c1", new Events());

      var lease = broker.acquire("c1", params("host", "192.168.1.118", "consumer", "bb-link"));

      assertThat(lease.get("ula")).isEqualTo("fd00::192.168.1.118");
      assertThat(lease.get("innerIpv4")).isEqualTo("169.254.9.1");
      assertThat(lease.get("token")).isNotNull();
      assertThat(lease.get("refs")).isEqualTo(1L);

      var tunnels = (List<?>) broker.status(params()).get("tunnels");
      assertThat(tunnels).hasSize(1);
      var only = (Map<?, ?>) tunnels.getFirst();
      assertThat(only.get("host")).isEqualTo("192.168.1.118");
      assertThat(only.get("state")).isEqualTo("UP");
      assertThat(only.get("consumers")).isEqualTo(List.of("bb-link"));
    }

    @Test
    @DisplayName("stable pins the constant 169.254.1.1 SMB address")
    void stablePins() throws Exception {
      broker.connected("c1", new Events());
      var lease = broker.acquire("c1", params("host", "192.168.1.118", "stable", true));
      assertThat(lease.get("innerIpv4")).isEqualTo("169.254.1.1");
    }

    @Test
    @DisplayName("two consumers share one tunnel; it stays up until the last releases")
    void sharedRefCount() throws Exception {
      broker.connected("c1", new Events());
      broker.connected("c2", new Events());

      var a = broker.acquire("c1", params("host", "192.168.1.118", "consumer", "bb-link"));
      var b = broker.acquire("c2", params("host", "192.168.1.118", "consumer", "blend"));

      assertThat(b.get("ula")).isEqualTo(a.get("ula"));
      assertThat(b.get("refs")).isEqualTo(2L);
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      assertThat(broker.release(params("token", (String) a.get("token"))).get("refs"))
          .isEqualTo(1L);
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      assertThat(broker.release(params("token", (String) b.get("token"))).get("refs"))
          .isEqualTo(0L);
      assertThat(tunnels.isOpen("192.168.1.118")).isFalse();
      assertThat(((List<?>) broker.status(params()).get("tunnels"))).isEmpty();
    }

    @Test
    @DisplayName("release of an unknown token is a no-op")
    void releaseIsIdempotent() {
      assertThat(broker.release(params("token", "nope")).get("refs")).isEqualTo(0L);
    }

    @Test
    @DisplayName("a connection's leases are released when it disconnects")
    void connectionScopedReclamation() throws Exception {
      broker.connected("c1", new Events());
      broker.acquire("c1", params("host", "192.168.1.118"));
      assertThat(tunnels.isOpen("192.168.1.118")).isTrue();

      broker.disconnected("c1");

      assertThat(tunnels.isOpen("192.168.1.118")).isFalse();
    }
  }

  @Nested
  @DisplayName("identity")
  class Identity {

    @Test
    @DisplayName("identify records the GUID so acquire {guid} rejoins the same tunnel")
    void guidRejoin() throws Exception {
      broker.connected("c1", new Events());
      var first = broker.acquire("c1", params("host", "192.168.1.118"));
      broker.identify(params("host", "192.168.1.118", "guid", "GUID-1", "pin", "2C1E7583"));

      broker.connected("c2", new Events());
      var rejoined = broker.acquire("c2", params("guid", "GUID-1", "consumer", "blend"));

      assertThat(rejoined.get("host")).isEqualTo("192.168.1.118");
      assertThat(rejoined.get("ula")).isEqualTo(first.get("ula"));
      assertThat(rejoined.get("refs")).isEqualTo(2L);
      assertThat(rejoined.get("guid")).isEqualTo("GUID-1");
    }

    @Test
    @DisplayName("acquire by an unknown GUID is refused")
    void unknownGuid() {
      broker.connected("c1", new Events());
      assertThatThrownBy(() -> broker.acquire("c1", params("guid", "GUID-X")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("unknown_guid"));
    }

    @Test
    @DisplayName("identify of a host with no tunnel is refused")
    void identifyUnknownHost() {
      assertThatThrownBy(() -> broker.identify(params("host", "10.0.0.1", "guid", "GUID-1")))
          .isInstanceOf(BrokerException.class)
          .satisfies(e -> assertThat(((BrokerException) e).code()).isEqualTo("unknown_host"));
    }

    @Test
    @DisplayName("identify tells the device's other consumers")
    void identifyNotifiesConsumers() throws Exception {
      var one = new Events();
      var two = new Events();
      broker.connected("c1", one);
      broker.connected("c2", two);
      broker.acquire("c1", params("host", "192.168.1.118"));
      broker.acquire("c2", params("host", "192.168.1.118"));

      broker.identify(params("host", "192.168.1.118", "guid", "GUID-1", "pin", "2C1E7583"));

      assertThat(one.received).anySatisfy(e -> assertThat(e.get("event")).isEqualTo("identity"));
      assertThat(two.received).anySatisfy(e -> assertThat(e.get("event")).isEqualTo("identity"));
    }
  }

  @Nested
  @DisplayName("drop events")
  class Drops {

    @Test
    @DisplayName("a spontaneous drop notifies holders and forgets the device")
    void dropNotifiesAndForgets() throws Exception {
      var events = new Events();
      broker.connected("c1", events);
      broker.acquire("c1", params("host", "192.168.1.118"));

      tunnels.drop("192.168.1.118", new IllegalStateException("link lost"));

      assertThat(events.received)
          .anySatisfy(
              e -> {
                assertThat(e.get("event")).isEqualTo("dropped");
                assertThat(e.get("host")).isEqualTo("192.168.1.118");
              });
      assertThat(((List<?>) broker.status(params()).get("tunnels"))).isEmpty();
    }
  }
}
