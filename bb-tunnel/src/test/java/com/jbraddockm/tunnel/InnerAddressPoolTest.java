package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link InnerAddressPool}: distinct per-session inner-IPv4 pairs with a stable override.
 */
@DisplayName("InnerAddressPool: per-session inner-IPv4 allocation")
class InnerAddressPoolTest {

  @Nested
  @DisplayName("allocate, for concurrent sessions")
  class Allocate {

    @Test
    @DisplayName("hands out distinct pairs starting at k=2, skipping the reserved blocks")
    void handsOutDistinctPairsFromKTwo() {
      InnerAddressPool pool = new InnerAddressPool();

      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.3.2", "169.254.3.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.4.2", "169.254.4.1"));
    }

    @Test
    @DisplayName("never hands out the USB (k=0) or stable (k=1) blocks")
    void neverHandsOutReservedBlocks() {
      InnerAddressPool pool = new InnerAddressPool();

      List<InnerIpv4> all = new ArrayList<>();
      for (int i = 0; i < 253; i++) {
        all.add(pool.allocate());
      }

      assertThat(all)
          .doesNotContain(
              new InnerIpv4("169.254.0.2", "169.254.0.1"),
              new InnerIpv4("169.254.1.2", "169.254.1.1"))
          .doesNotContain(new InnerIpv4("169.254.255.2", "169.254.255.1"))
          .hasSize(253)
          .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("throws once all 253 non-stable blocks are in use")
    void throwsWhenExhausted() {
      InnerAddressPool pool = new InnerAddressPool();
      for (int i = 0; i < 253; i++) {
        pool.allocate();
      }

      assertThatThrownBy(pool::allocate)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("exhausted");
    }
  }

  @Nested
  @DisplayName("allocateStable, for a constant external-app SMB address")
  class AllocateStable {

    @Test
    @DisplayName("grants 169.254.1.1/.2 and keeps it out of allocate()")
    void grantsTheStablePair() {
      InnerAddressPool pool = new InnerAddressPool();

      assertThat(pool.allocateStable()).isEqualTo(new InnerIpv4("169.254.1.2", "169.254.1.1"));
      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
    }

    @Test
    @DisplayName("is available even after other sessions have allocated")
    void staysAvailableAlongsideOtherSessions() {
      InnerAddressPool pool = new InnerAddressPool();
      pool.allocate();
      pool.allocate();

      assertThat(pool.allocateStable()).isEqualTo(new InnerIpv4("169.254.1.2", "169.254.1.1"));
    }

    @Test
    @DisplayName("throws if the stable pair is already in use")
    void throwsWhenStableAlreadyTaken() {
      InnerAddressPool pool = new InnerAddressPool();
      pool.allocateStable();

      assertThatThrownBy(pool::allocateStable)
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("already in use");
    }
  }

  @Nested
  @DisplayName("release, returning a pair to the pool")
  class Release {

    @Test
    @DisplayName("frees an allocated block for reuse")
    void freesAllocatedBlockForReuse() {
      InnerAddressPool pool = new InnerAddressPool();
      InnerIpv4 first = pool.allocate();
      pool.allocate();

      pool.release(first);

      assertThat(pool.allocate()).isEqualTo(first);
    }

    @Test
    @DisplayName("frees the stable pair so it can be taken again")
    void freesTheStablePair() {
      InnerAddressPool pool = new InnerAddressPool();
      InnerIpv4 stable = pool.allocateStable();

      pool.release(stable);

      assertThat(pool.allocateStable()).isEqualTo(stable);
    }

    @Test
    @DisplayName("ignores a pair the pool never handed out")
    void ignoresUnknownPair() {
      InnerAddressPool pool = new InnerAddressPool();

      pool.release(new InnerIpv4("169.254.0.2", "169.254.0.1"));

      assertThat(pool.allocate()).isEqualTo(new InnerIpv4("169.254.2.2", "169.254.2.1"));
    }
  }

  @Nested
  @DisplayName("InnerIpv4 validation")
  class Validation {

    @Test
    @DisplayName("rejects a non-link-local address")
    void rejectsNonLinkLocal() {
      assertThatThrownBy(() -> new InnerIpv4("10.0.0.2", "169.254.1.1"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("169.254");
    }
  }
}
