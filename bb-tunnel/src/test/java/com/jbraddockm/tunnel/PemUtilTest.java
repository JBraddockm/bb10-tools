package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link PemUtil}: loading DER blocks of a given PEM type from the classpath and from files. */
@DisplayName("PemUtil: loading DER blocks out of PEM")
class PemUtilTest {

  @Nested
  @DisplayName("from a classpath resource")
  class FromClasspath {

    @Test
    @DisplayName("reads the bundled certificate chain and the single PKCS#8 key")
    void readsTheBundledChainAndKey() throws Exception {
      assertThat(PemUtil.readBlocks("/certs/blend_chain.pem", "CERTIFICATE"))
          .isNotEmpty()
          .allSatisfy(der -> assertThat(der).isNotEmpty());
      assertThat(PemUtil.readBlocks("/certs/blend_key_p8.pem", "PRIVATE KEY")).hasSize(1);
    }

    @Test
    @DisplayName("returns nothing when no block has the requested type, rather than failing")
    void filtersByType() throws Exception {
      assertThat(PemUtil.readBlocks("/certs/blend_key_p8.pem", "CERTIFICATE")).isEmpty();
    }

    @Test
    @DisplayName("throws when the resource is not on the classpath")
    void missingResourceThrows() {
      assertThatThrownBy(() -> PemUtil.readBlocks("/certs/does-not-exist.pem", "CERTIFICATE"))
          .isInstanceOf(FileNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("from a file on disk")
  class FromFile {

    @Test
    @DisplayName("reads the same blocks a classpath read would")
    void readsBlocksFromAFile(@TempDir Path dir) throws Exception {
      Path pem = dir.resolve("chain.pem");
      try (InputStream is = PemUtilTest.class.getResourceAsStream("/certs/blend_chain.pem")) {
        Files.copy(is, pem);
      }

      var fromFile = PemUtil.readBlocksFromFile(pem, "CERTIFICATE");
      var fromClasspath = PemUtil.readBlocks("/certs/blend_chain.pem", "CERTIFICATE");
      assertThat(fromFile).isNotEmpty().hasSameSizeAs(fromClasspath); // byte[] equals is identity,
      for (int i = 0; i < fromFile.size(); i++) // so compare contents element by element
        assertThat(fromFile.get(i)).containsExactly(fromClasspath.get(i));
    }
  }
}
