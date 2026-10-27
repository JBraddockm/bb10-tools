package com.jbraddockm.link.transport.tls;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import java.io.IOException;
import java.nio.file.Path;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link FixedClientKeyManager}: one client certificate and key, offered in every
 * handshake.
 */
@DisplayName("FixedClientKeyManager: offers the one certificate of the client")
class FixedClientKeyManagerTest {

  private static final String ALIAS = "bb-link-client";

  @TempDir private Path folder;
  private PemIdentity.Loaded loaded;
  private FixedClientKeyManager manager;

  @BeforeEach
  void loadThePair() throws IOException {
    loaded =
        PemIdentity.load(
            new ClientCertificate(
                Resources.copyTo(TestPki.class, "test-pki--leaf-certificate.pem", folder),
                Resources.copyTo(TestPki.class, "test-pki--leaf-key-pkcs8.pem", folder)));
    manager = new FixedClientKeyManager(loaded);
  }

  @Test
  @DisplayName(
      "has one alias for a client, whatever the key type and the issuers that the server lists")
  void hasOneAlias() {
    assertThat(manager.getClientAliases("RSA", null)).containsExactly(ALIAS);
    assertThat(manager.chooseClientAlias(new String[] {"EC", "RSA"}, null, null)).isEqualTo(ALIAS);
  }

  @Test
  @DisplayName("gives the chain and the private key for its alias, and nothing for another")
  void givesThePairForItsAlias() {
    assertThat(manager.getCertificateChain(ALIAS)).isEqualTo(loaded.chain());
    assertThat(manager.getPrivateKey(ALIAS)).isSameAs(loaded.key());
    assertThat(manager.getCertificateChain("other")).isNull();
    assertThat(manager.getPrivateKey("other")).isNull();
  }

  @Test
  @DisplayName("gives a copy of the chain: changing what it gave does not change what it offers")
  void givesACopyOfTheChain() {
    var first = manager.getCertificateChain(ALIAS);
    first[0] = null;

    assertThat(manager.getCertificateChain(ALIAS)[0]).isNotNull();
  }

  @Test
  @DisplayName(
      "keeps a copy of the chain it was made with: changing the array afterwards changes nothing")
  void keepsACopyOfTheChain() {
    X509Certificate[] chain = loaded.chain();
    var local = new FixedClientKeyManager(new PemIdentity.Loaded(loaded.key(), chain));

    chain[0] = null;

    assertThat(local.getCertificateChain(ALIAS)[0]).isNotNull();
  }

  @Test
  @DisplayName("does not answer for a server: it has no alias, no choice and no key as a server")
  void doesNotAnswerForAServer() {
    assertThat(manager.getServerAliases("RSA", null)).isNull();
    assertThat(manager.chooseServerAlias("RSA", null, null)).isNull();
  }
}
