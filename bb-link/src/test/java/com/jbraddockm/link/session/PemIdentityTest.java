package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link PemIdentity}: reading a client certificate and key from PEM, in PKCS #8, PKCS #1
 * and encrypted forms.
 */
@DisplayName("PemIdentity: the certificate and the key that a session offers")
class PemIdentityTest {

  @TempDir private Path folder;

  private Path copy(String name) {
    for (var owner : List.of(PemIdentityTest.class, TestPki.class, TestKeys.class)) {
      if (owner.getResource(name) != null) {
        return Resources.copyTo(owner, name, folder);
      }
    }
    throw new AssertionError("no test resource " + name);
  }

  private PemIdentity.Loaded load(String certificate, String key) throws IOException {
    return PemIdentity.load(new ClientCertificate(copy(certificate), copy(key)));
  }

  @Nested
  @DisplayName("what it reads")
  class Reading {

    @Test
    @DisplayName("an RSA key as PKCS #8 with its certificate: the key, and a chain of one")
    void anRsaKeyAsPkcs8() throws Exception {
      var loaded = load("test-pki--leaf-certificate.pem", "test-pki--leaf-key-pkcs8.pem");

      assertThat(loaded.key().getAlgorithm()).isEqualTo("RSA");
      assertThat(loaded.chain()).hasSize(1);
      assertThat(((RSAPrivateKey) loaded.key()).getModulus())
          .isEqualTo(((RSAPublicKey) loaded.chain()[0].getPublicKey()).getModulus());
    }

    @Test
    @DisplayName("an RSA key in the older form, PKCS #1: the same key as the PKCS #8 of it")
    void anRsaKeyAsPkcs1() throws Exception {
      var fromPkcs1 =
          (RSAPrivateKey)
              load("test-pki--leaf-certificate.pem", "test-pki--leaf-key-pkcs1.pem").key();
      var fromPkcs8 =
          (RSAPrivateKey)
              load("test-pki--leaf-certificate.pem", "test-pki--leaf-key-pkcs8.pem").key();

      assertThat(fromPkcs1.getModulus()).isEqualTo(fromPkcs8.getModulus());
      assertThat(fromPkcs1.getPrivateExponent()).isEqualTo(fromPkcs8.getPrivateExponent());
    }

    @Test
    @DisplayName("an EC key as PKCS #8 with its certificate")
    void anEcKey() throws Exception {
      var loaded = load("identity--ec-certificate.pem", "test-key--ec-p521.pem");

      assertThat(loaded.key().getAlgorithm()).isEqualTo("EC");
      assertThat(loaded.chain()[0].getPublicKey().getAlgorithm()).isEqualTo("EC");
    }

    @Test
    @DisplayName("a certificate that is a chain, the certificate of the client first")
    void aChain() throws Exception {
      var loaded = load("identity--chain.pem", "test-pki--leaf-key-pkcs8.pem");

      assertThat(loaded.chain()).hasSize(2);
      assertThat(loaded.chain()[0].getSubjectX500Principal().getName()).contains("BBCGIAUTH");
    }

    @Test
    @DisplayName("a certificate and a key with the text that an export writes before a block")
    void textBeforeABlock() throws Exception {
      var loaded =
          load(
              "identity--certificate-with-bag-attributes.pem",
              "identity--key-with-bag-attributes.pem");

      assertThat(loaded.key().getAlgorithm()).isEqualTo("RSA");
      assertThat(loaded.chain()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusals {

    @ParameterizedTest(name = "{0} with {1}: {2}")
    @CsvSource({
      "identity--certificate-empty.pem, test-pki--leaf-key-pkcs8.pem, clientCertificate, contains"
          + " no certificate",
      "identity--certificate-garbage.txt, test-pki--leaf-key-pkcs8.pem, clientCertificate, is not a"
          + " PEM certificate",
      "test-pki--leaf-certificate.pem, identity--key-text-without-block.txt, clientKey, contains no"
          + " private key",
      "test-pki--leaf-certificate.pem, identity--key-binary.bin, clientKey, is not a PEM text file",
      "test-pki--leaf-certificate.pem, test-pki--leaf-key-encrypted.pem, clientKey, is encrypted",
      "test-pki--leaf-certificate.pem, identity--key-rsa-traditional-encrypted.pem, clientKey, is"
          + " encrypted",
      "test-pki--leaf-certificate.pem, test-pki--ec-key-sec1.pem, clientKey, is an EC key in the"
          + " older form",
      "test-pki--leaf-certificate.pem, identity--key-dsa-label.pem, clientKey, has an unsupported"
          + " kind of key: DSA PRIVATE KEY",
      "test-pki--leaf-certificate.pem, identity--key-bad-base64.pem, clientKey, is not valid PEM",
      "test-pki--leaf-certificate.pem, identity--key-pkcs8-garbage.pem, clientKey, is not an RSA or"
          + " EC private key",
      "test-pki--leaf-certificate.pem, identity--key-pkcs1-garbage.pem, clientKey, is not an RSA or"
          + " EC private key",
      "test-pki--leaf-certificate.pem, identity--key-ed25519.pem, clientKey, is not an RSA or EC"
          + " private key"
    })
    @DisplayName("what it cannot read, naming the file that is wrong and saying why")
    void whatItCannotRead(String certificate, String key, String parameter, String reason) {
      assertThatThrownBy(() -> load(certificate, key))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
          .hasMessageContaining(reason);
    }

    @ParameterizedTest(name = "{0} with {1}")
    @CsvSource({
      "test-pki--leaf-certificate.pem, test-pki--other-key-pkcs8.pem",
      "identity--certificate-other-key.pem, test-pki--leaf-key-pkcs8.pem",
      "test-pki--leaf-certificate.pem, test-key--ec-p521.pem",
      "identity--ec-certificate.pem, test-pki--leaf-key-pkcs8.pem"
    })
    @DisplayName(
        "a key that does not belong to the certificate: another RSA key, or a key of another kind")
    void aKeyThatDoesNotBelong(String certificate, String key) {
      assertThatThrownBy(() -> load(certificate, key))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("clientKey"))
          .hasMessageContaining("does not belong to the certificate in clientCertificate");
    }

    @Test
    @DisplayName(
        "a key that cannot be read as it is encrypted tells the command that makes a copy that can")
    void anEncryptedKeyTellsTheCommand() {
      assertThatThrownBy(
              () -> load("test-pki--leaf-certificate.pem", "test-pki--leaf-key-encrypted.pem"))
          .hasMessageContaining("openssl pkcs8 -topk8 -nocrypt");
    }

    @Test
    @DisplayName("a file that is not there is an error of reading, not of the argument")
    void aFileThatIsNotThere() {
      var missing =
          new ClientCertificate(
              folder.resolve("nothing.pem"), copy("test-pki--leaf-key-pkcs8.pem"));

      assertThatThrownBy(() -> PemIdentity.load(missing)).isInstanceOf(NoSuchFileException.class);
    }
  }
}
