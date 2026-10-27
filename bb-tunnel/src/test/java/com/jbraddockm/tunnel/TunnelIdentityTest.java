package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.tunnel.internal.PemUtil;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link TunnelIdentity}: deriving a bp2p tunnel ULA from a certificate. The known-answer
 * case pins the bundled {@code CN=blend_client_9665} leaf to the identity the device accepts.
 */
@DisplayName("TunnelIdentity: deriving a tunnel ULA from a certificate")
class TunnelIdentityTest {

  private static byte[] bundledLeafDer() throws Exception {
    return PemUtil.readBlocks("/certs/blend_chain.pem", "CERTIFICATE").getFirst();
  }

  @Nested
  @DisplayName("our ULA, derived from the leaf with an all-zero prefix")
  class OurUla {

    @Test
    @DisplayName("is the identity the device accepts for the bundled leaf")
    void matchesTheEnrolledIdentity() throws Exception {
      assertThat(TunnelIdentity.ourUla(bundledLeafDer()))
          .isEqualTo("fd00:0000:0000:2fc2:4132:a6e5:2b64:8fdb");
    }

    @Test
    @DisplayName("is a 16-byte fd00::/8 ULA whose five prefix bytes are zero")
    void isAUlaWithAZeroPrefix() throws Exception {
      byte[] ula = TunnelIdentity.ourUlaBytes(bundledLeafDer());
      assertThat(ula).hasSize(16);
      assertThat(ula[0] & 0xff).isEqualTo(0xfd);
      assertThat(Arrays.copyOfRange(ula, 1, 6)).containsOnly((byte) 0);
    }
  }

  @Nested
  @DisplayName("the general derivation 0xfd | prefix | SHA1(prefix || SHA1(certDER))[10:20]")
  class Derivation {

    @Test
    @DisplayName("tags the address with 0xfd and copies the five prefix bytes verbatim")
    void copiesThePrefixAndTagsFd() throws Exception {
      byte[] prefix = {0x32, 0x5b, (byte) 0xd4, 0x58, (byte) 0xd2};
      byte[] ula = TunnelIdentity.deriveUla(bundledLeafDer(), prefix);
      assertThat(ula).hasSize(16);
      assertThat(ula[0] & 0xff).isEqualTo(0xfd);
      assertThat(Arrays.copyOfRange(ula, 1, 6)).containsExactly(prefix);
    }

    @Test
    @DisplayName("is deterministic for the same certificate and prefix")
    void isDeterministic() throws Exception {
      byte[] der = bundledLeafDer();
      assertThat(TunnelIdentity.deriveUla(der, new byte[5]))
          .containsExactly(TunnelIdentity.deriveUla(der, new byte[5]));
    }

    @Test
    @DisplayName("refuses a prefix that is not exactly five bytes")
    void prefixMustBeFiveBytes() throws Exception {
      byte[] der = bundledLeafDer();
      assertThatThrownBy(() -> TunnelIdentity.deriveUla(der, new byte[4]))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> TunnelIdentity.deriveUla(der, new byte[6]))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }
}
