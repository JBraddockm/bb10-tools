package com.jbraddockm.link.endpoint.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FriendlyIdentity}: the bp2p Friendly instance-name and its seed-salted token.
 */
@DisplayName("FriendlyIdentity: the Friendly service instance name")
class FriendlyIdentityTest {

  private static final HexFormat HEX = HexFormat.of();

  @Nested
  @DisplayName("the deviceHash fold SHA1(seed || id)[12:20]")
  class DeviceHash {

    @Test
    @DisplayName("reproduces device C239E313's captured advert (seed 33C9D3A9…, id 2C1E7583)")
    void reproducesTheCapturedToken() {
      byte[] seed = HEX.parseHex("33C9D3A93B9DD79F");
      byte[] token = FriendlyIdentity.deviceHash("2C1E7583", seed);
      assertThat(HEX.formatHex(token)).isEqualToIgnoringCase("9B2770A11C414366");
    }

    @Test
    @DisplayName("is 8 bytes and depends on both seed and identifier")
    void isEightBytesAndSeedDependent() {
      byte[] seed = HEX.parseHex("0000000000000000");
      assertThat(FriendlyIdentity.deviceHash("2C1E7583", seed)).hasSize(8);
      assertThat(FriendlyIdentity.deviceHash("2C1E7583", seed))
          .isNotEqualTo(FriendlyIdentity.deviceHash("2C1E7583", HEX.parseHex("1111111111111111")));
      assertThat(FriendlyIdentity.deviceHash("2C1E7583", seed))
          .isNotEqualTo(FriendlyIdentity.deviceHash("DEADBEEF", seed));
    }
  }

  @Nested
  @DisplayName("the instance name")
  class InstanceName {

    @Test
    @DisplayName("is Friendly_<SEED>_<DEVICEHASH> in upper-case hex, as seen on the wire")
    void matchesTheWireFormat() {
      byte[] seed = HEX.parseHex("33C9D3A93B9DD79F");
      byte[] hash = FriendlyIdentity.deviceHash("2C1E7583", seed);
      assertThat(FriendlyIdentity.instanceName(seed, hash))
          .isEqualTo("Friendly_33C9D3A93B9DD79F_9B2770A11C414366");
    }
  }

  @Nested
  @DisplayName("the seed")
  class Seed {

    @Test
    @DisplayName("is 8 fresh random bytes each time")
    void isEightFreshBytes() {
      assertThat(FriendlyIdentity.newSeed()).hasSize(8);
      assertThat(FriendlyIdentity.newSeed()).isNotEqualTo(FriendlyIdentity.newSeed());
    }
  }
}
