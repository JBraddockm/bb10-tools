package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TunnelConfig}: the inputs to a tunnel and the two factories that build them. */
@DisplayName("TunnelConfig: the inputs to a tunnel")
class TunnelConfigTest {

  private static Path copyResource(String resource, Path to) throws Exception {
    try (InputStream is = TunnelConfigTest.class.getResourceAsStream(resource)) {
      Files.copy(is, to);
    }
    return to;
  }

  @Nested
  @DisplayName("fromClasspath, using the bundled Blend identity")
  class FromClasspath {

    @Test
    @DisplayName("applies the host and the Blend defaults (control 1111, Blend 2708, loopback IPv4)")
    void appliesTheHostAndDefaults() {
      TunnelConfig cfg = TunnelConfig.fromClasspath("192.168.1.118");
      assertThat(cfg.host()).isEqualTo("192.168.1.118");
      assertThat(cfg.controlPort()).isEqualTo(1111);
      assertThat(cfg.blendPort()).isEqualTo(2708);
      assertThat(cfg.pcIpv4()).isEqualTo("127.0.0.2");
      assertThat(cfg.deviceIpv4()).isEqualTo("127.0.0.1");
      assertThat(cfg.certChainDer()).isNotEmpty();
      assertThat(cfg.privateKeyDer()).isNotEmpty();
    }

    @Test
    @DisplayName("treats the leaf as the first element of the chain")
    void leafIsTheFirstChainElement() {
      TunnelConfig cfg = TunnelConfig.fromClasspath("10.0.0.1");
      assertThat(cfg.leafCertDer()).containsExactly(cfg.certChainDer().get(0));
    }
  }

  @Nested
  @DisplayName("fromFiles, reading the identity off disk")
  class FromFiles {

    @Test
    @DisplayName("reads the same cert and key as the bundled identity")
    void readsTheIdentityOffDisk(@TempDir Path dir) throws Exception {
      Path chain = copyResource("/certs/blend_chain.pem", dir.resolve("chain.pem"));
      Path key = copyResource("/certs/blend_key_p8.pem", dir.resolve("key.pem"));

      TunnelConfig onDisk = TunnelConfig.fromFiles("169.254.0.1", chain, key);
      TunnelConfig bundled = TunnelConfig.fromClasspath("169.254.0.1");

      assertThat(onDisk.host()).isEqualTo("169.254.0.1");
      assertThat(onDisk.controlPort()).isEqualTo(1111);
      assertThat(onDisk.leafCertDer()).containsExactly(bundled.leafCertDer());
      assertThat(onDisk.privateKeyDer()).containsExactly(bundled.privateKeyDer());
    }

    @Test
    @DisplayName("wraps a missing identity file in an IllegalStateException")
    void wrapsAMissingIdentityInIllegalState(@TempDir Path dir) {
      Path missing = dir.resolve("nope.pem");
      assertThatThrownBy(() -> TunnelConfig.fromFiles("10.0.0.1", missing, missing))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("client identity");
    }
  }
}
