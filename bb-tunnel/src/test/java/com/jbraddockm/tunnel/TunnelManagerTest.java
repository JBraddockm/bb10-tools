package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TunnelManager}: one shared, ref-counted tunnel per device, with a fake opener.
 */
@DisplayName("TunnelManager: one shared, ref-counted tunnel per device")
class TunnelManagerTest {

  private static final Tunnels.Identity ID =
      new Tunnels.Identity(java.nio.file.Path.of("cert.pem"), java.nio.file.Path.of("key.pem"));

  private final FakeOpener opener = new FakeOpener();
  private final TunnelManager manager = new TunnelManager(new InnerAddressPool(), opener);

  /**
   * Stands in for the real bring-up: records opens (the pair) and closes, hands back a canned ULA.
   */
  private static final class FakeOpener implements TunnelManager.Opener {
    final List<InnerIpv4> opened = new ArrayList<>();
    final List<String> closed = new ArrayList<>();
    final Map<String, Consumer<Throwable>> dropCallbacks = new HashMap<>();
    boolean fail;

    @Override
    public TunnelManager.Connection open(
        String host, InnerIpv4 inner, Tunnels.Identity identity, Consumer<Throwable> onDrop) {
      if (fail) {
        throw new IllegalStateException("bring-up refused");
      }
      opened.add(inner);
      dropCallbacks.put(host, onDrop); // let a test fire a spontaneous drop
      return new TunnelManager.Connection() {
        @Override
        public String deviceUla() {
          return "fd00::" + host;
        }

        @Override
        public void close() {
          closed.add(host);
        }
      };
    }
  }

  @Nested
  @DisplayName("per-device allocation")
  class Allocation {

    @Test
    @DisplayName("gives each device a distinct inner IPv4 and reports its ULA")
    void distinctPerDevice() throws Exception {
      var a = manager.acquire("169.254.0.1", false, ID);
      var b = manager.acquire("192.168.1.50", false, ID);

      assertThat(a.deviceIpv4()).isEqualTo("169.254.2.1");
      assertThat(b.deviceIpv4()).isEqualTo("169.254.3.1");
      assertThat(a.deviceUla()).isEqualTo("fd00::169.254.0.1");
      assertThat(opener.opened).hasSize(2);
    }

    @Test
    @DisplayName("stable pins 169.254.1.1 for the external-app mount")
    void stablePinsTheConstantAddress() throws Exception {
      assertThat(manager.acquire("169.254.0.1", true, ID).deviceIpv4()).isEqualTo("169.254.1.1");
    }
  }

  @Nested
  @DisplayName("sharing one tunnel per device")
  class Sharing {

    @Test
    @DisplayName("a second acquire of the same device reuses the tunnel, does not reopen it")
    void reusesTheTunnel() throws Exception {
      var first = manager.acquire("169.254.0.1", false, ID);
      var second = manager.acquire("169.254.0.1", false, ID);

      assertThat(second.deviceIpv4()).isEqualTo(first.deviceIpv4());
      assertThat(opener.opened).hasSize(1);
      assertThat(manager.isOpen("169.254.0.1")).isTrue();
    }

    @Test
    @DisplayName("stays up until the last lease closes, then comes down")
    void refCounted() throws Exception {
      var first = manager.acquire("169.254.0.1", false, ID);
      var second = manager.acquire("169.254.0.1", false, ID);

      first.close();
      assertThat(opener.closed).isEmpty();
      assertThat(manager.isOpen("169.254.0.1")).isTrue();

      second.close();
      assertThat(opener.closed).containsExactly("169.254.0.1");
      assertThat(manager.isOpen("169.254.0.1")).isFalse();
    }

    @Test
    @DisplayName("close is idempotent")
    void closeIsIdempotent() throws Exception {
      var lease = manager.acquire("169.254.0.1", false, ID);

      lease.close();
      lease.close();

      assertThat(opener.closed).containsExactly("169.254.0.1");
    }
  }

  @Nested
  @DisplayName("returning addresses to the pool")
  class Returning {

    @Test
    @DisplayName("a closed tunnel's address is reused by the next device")
    void addressIsReused() throws Exception {
      manager.acquire("169.254.0.1", false, ID).close();

      assertThat(manager.acquire("192.168.1.50", false, ID).deviceIpv4()).isEqualTo("169.254.2.1");
    }

    @Test
    @DisplayName("a failed open returns the pair to the pool")
    void failedOpenReturnsThePair() throws Exception {
      opener.fail = true;
      assertThatThrownBy(() -> manager.acquire("169.254.0.1", false, ID))
          .isInstanceOf(IllegalStateException.class);

      opener.fail = false;
      assertThat(manager.acquire("192.168.1.50", false, ID).deviceIpv4()).isEqualTo("169.254.2.1");
    }

    @Test
    @DisplayName("a spontaneous drop evicts the tunnel, notifies onDrop, and frees the address")
    void dropEvictsNotifiesAndFrees() throws Exception {
      var dropped = new ArrayList<String>();
      manager.onDrop((host, _) -> dropped.add(host));
      manager.acquire("169.254.0.1", false, ID);

      opener.dropCallbacks.get("169.254.0.1").accept(new IllegalStateException("link lost"));

      assertThat(dropped).containsExactly("169.254.0.1");
      assertThat(manager.isOpen("169.254.0.1")).isFalse();
      assertThat(manager.acquire("192.168.1.50", false, ID).deviceIpv4()).isEqualTo("169.254.2.1");
    }

    @Test
    @DisplayName("a clean last-lease close does not fire onDrop")
    void cleanCloseDoesNotFireOnDrop() throws Exception {
      var dropped = new ArrayList<String>();
      manager.onDrop((host, _) -> dropped.add(host));

      manager.acquire("169.254.0.1", false, ID).close();

      assertThat(dropped).isEmpty();
      assertThat(opener.closed).containsExactly("169.254.0.1");
    }
  }
}
