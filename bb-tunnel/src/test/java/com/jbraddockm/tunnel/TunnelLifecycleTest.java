package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Tunnel}'s lifecycle that don't need a device, exercised through the
 * package-private constructor (a handle that never brought the tunnel up). Bring-up, drop detection,
 * and restart are device-coupled and verified live instead.
 */
@DisplayName("Tunnel: the lifecycle of a handle")
@SuppressWarnings("resource") // handles come from the package-private ctor: no device, nothing to close
class TunnelLifecycleTest {

  private static Tunnel handle() {
    return new Tunnel(TunnelConfig.fromClasspath("192.168.1.118"));
  }

  @Nested
  @DisplayName("a fresh handle, before bring-up")
  class Fresh {

    @Test
    @DisplayName("is CONNECTING, not open, with no device ULA and no failure yet")
    void isConnectingAndNotOpen() {
      Tunnel t = handle();
      assertThat(t.state()).isEqualTo(Tunnel.State.CONNECTING);
      assertThat(t.isOpen()).isFalse();
      assertThat(t.failure()).isNull();
      assertThat(t.deviceUla()).isNull();
    }

    @Test
    @DisplayName("already exposes our cert-derived ULA (it needs no device)")
    void exposesTheCertDerivedOurUla() {
      assertThat(handle().ourUla()).isEqualTo("fd00:0000:0000:2fc2:4132:a6e5:2b64:8fdb");
    }
  }

  @Nested
  @DisplayName("closing a handle")
  class Closing {

    @Test
    @DisplayName("moves it to CLOSED and no longer open")
    void transitionsToClosed() {
      Tunnel t = handle();
      t.close();
      assertThat(t.state()).isEqualTo(Tunnel.State.CLOSED);
      assertThat(t.isOpen()).isFalse();
      assertThat(t.failure()).isNull();
    }

    @Test
    @DisplayName("fires the onClosed listener exactly once, with a null cause for a clean close")
    void firesTheListenerOnceWithNullCause() {
      AtomicInteger calls = new AtomicInteger();
      AtomicReference<Throwable> seen = new AtomicReference<>(new RuntimeException("sentinel"));

      Tunnel t = handle();
      t.onClosed(
          cause -> {
            calls.incrementAndGet();
            seen.set(cause);
          });

      t.close();
      t.close(); // idempotent — must not fire the listener again

      assertThat(calls).hasValue(1);
      assertThat(seen.get()).isNull();
    }

    @Test
    @DisplayName("is safe when no listener was registered")
    void isSafeWithoutAListener() {
      Tunnel t = handle();
      t.close();
      assertThat(t.state()).isEqualTo(Tunnel.State.CLOSED);
    }
  }

  @Nested
  @DisplayName("onClosed")
  class OnClosed {

    @Test
    @DisplayName("returns the same handle so calls can be chained")
    void returnsTheSameHandleForChaining() {
      Tunnel t = handle();
      assertThat(t.onClosed(_ -> {})).isSameAs(t);
    }
  }
}
