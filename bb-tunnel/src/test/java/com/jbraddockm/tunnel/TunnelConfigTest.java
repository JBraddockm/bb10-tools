package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TunnelConfig}: the inputs to a tunnel and the two factories that build them. */
@DisplayName("TunnelConfig: the inputs to a tunnel")
class TunnelConfigTest {

  private static Path copyResource(String resource, Path to) throws Exception {
    try (InputStream is = TunnelConfigTest.class.getResourceAsStream(resource)) {
      Files.copy(Objects.requireNonNull(is, resource), to);
    }
    return to;
  }

  @Nested
  @DisplayName("fromClasspath, using the bundled client identity")
  class FromClasspath {

    @Test
    @DisplayName("applies the host and the defaults (control 1111, link-local 169.254/16 IPv4)")
    void appliesTheHostAndDefaults() {
      TunnelConfig cfg = TunnelConfig.fromClasspath("192.168.1.118");
      assertThat(cfg.host()).isEqualTo("192.168.1.118");
      assertThat(cfg.controlPort()).isEqualTo(1111);
      assertThat(cfg.pcIpv4()).isEqualTo("169.254.1.2");
      assertThat(cfg.deviceIpv4()).isEqualTo("169.254.1.1");
      assertThat(cfg.certChainDer()).isNotEmpty();
      assertThat(cfg.privateKeyDer()).isNotEmpty();
    }

    @Test
    @DisplayName("treats the leaf as the first element of the chain")
    void leafIsTheFirstChainElement() {
      TunnelConfig cfg = TunnelConfig.fromClasspath("10.0.0.1");
      assertThat(cfg.leafCertDer()).containsExactly(cfg.certChainDer().getFirst());
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
    @DisplayName("reports a missing identity file as a typed cert error with a code")
    void reportsAMissingIdentity(@TempDir Path dir) {
      Path missing = dir.resolve("nope.pem");
      var thrown = catchThrowable(() -> TunnelConfig.fromFiles("10.0.0.1", missing, missing));
      assertThat(thrown)
          .isInstanceOf(TunnelCertificateException.class)
          .hasMessageContaining("not found");
      assertThat(((TunnelCertificateException) thrown).code()).isEqualTo("cert_not_found");
    }

    @Test
    @DisplayName("reports a malformed certificate as cert_malformed")
    void reportsAMalformedCertificate(@TempDir Path dir) throws Exception {
      Path notPem = Files.writeString(dir.resolve("chain.pem"), "not a PEM file");
      Path key = copyResource("/certs/blend_key_p8.pem", dir.resolve("key.pem"));
      var thrown = catchThrowable(() -> TunnelConfig.fromFiles("10.0.0.1", notPem, key));
      assertThat(thrown).isInstanceOf(TunnelCertificateException.class);
      assertThat(((TunnelCertificateException) thrown).code()).isEqualTo("cert_malformed");
    }

    @Test
    @DisplayName("reads a SEC1 EC key (BEGIN EC PRIVATE KEY) by wrapping it as PKCS#8 for BC")
    void readsASec1EcKey(@TempDir Path dir) throws Exception {
      Path chain = copyResource("/certs/blend_chain.pem", dir.resolve("chain.pem"));

      // A device's own *key.pem is often SEC1 (OpenSSL traditional), not PKCS#8 — and OpenSSL embeds
      // the named curve in the SEC1 structure. Build one the same way.
      var generator = java.security.KeyPairGenerator.getInstance("EC");
      generator.initialize(new java.security.spec.ECGenParameterSpec("secp521r1"));
      var pkcs8 =
          org.bouncycastle.asn1.pkcs.PrivateKeyInfo.getInstance(
              generator.generateKeyPair().getPrivate().getEncoded());
      var curve = pkcs8.getPrivateKeyAlgorithm().getParameters();
      var bare = org.bouncycastle.asn1.sec.ECPrivateKey.getInstance(pkcs8.parsePrivateKey());
      var sec1Der =
          new org.bouncycastle.asn1.sec.ECPrivateKey(521, bare.getKey(), curve).getEncoded("DER");
      Path key = Files.writeString(dir.resolve("sec1.pem"), pem("EC PRIVATE KEY", sec1Der));

      TunnelConfig cfg = TunnelConfig.fromFiles("169.254.0.1", chain, key);

      // The loaded key must be PKCS#8 that BouncyCastle's PrivateKeyFactory accepts (what the TLS
      // client uses) — the exact step that failed when SEC1 bytes were passed through unwrapped.
      assertThat(org.bouncycastle.crypto.util.PrivateKeyFactory.createKey(cfg.privateKeyDer()))
          .isNotNull();
    }

    private static String pem(String type, byte[] der) {
      var body = java.util.Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
      return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n";
    }
  }
}
