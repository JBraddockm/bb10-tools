package com.jbraddockm.link.endpoint.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigInteger;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RsaKeys} and {@link EcKeys}: key generation for the client certificate and its
 * authority.
 */
@DisplayName("RsaKeys and EcKeys: the key pairs that are made")
class KeysTest {

  @Nested
  @DisplayName("RsaKeys")
  class Rsa {

    @Test
    @DisplayName("the key of a device is 2048 bits")
    void theDeviceKeyIs2048Bits() {
      assertThat(RsaKeys.DEVICE_KEY_BITS).isEqualTo(2048);
    }

    @Test
    @DisplayName("generate makes an RSA key of the size asked for, with the public exponent 65537")
    void generateMakesTheSizeAskedFor() throws Exception {
      var keys = RsaKeys.generate(1024);

      assertThat(keys.getPublic().getAlgorithm()).isEqualTo("RSA");
      assertThat(((RSAPublicKey) keys.getPublic()).getModulus().bitLength()).isEqualTo(1024);
      assertThat(((RSAPublicKey) keys.getPublic()).getPublicExponent())
          .isEqualTo(BigInteger.valueOf(65537));
    }

    @Test
    @DisplayName("generate makes a different key each time")
    void generateMakesANewKeyEachTime() throws Exception {
      assertThat(RsaKeys.generate(1024).getPublic())
          .isNotEqualTo(RsaKeys.generate(1024).getPublic());
    }

    @Test
    @DisplayName("generate refuses a size that is too small to be a key")
    void generateRefusesATinySize() {
      assertThatIllegalArgumentException().isThrownBy(() -> RsaKeys.generate(100));
    }
  }

  @Nested
  @DisplayName("EcKeys")
  class Ec {

    @Test
    @DisplayName("generateP521 makes a key on the curve P-521, with its private key in PKCS #8")
    void generateP521MakesAP521Key() throws Exception {
      var keys = EcKeys.generateP521();

      assertThat(keys.getPublic().getAlgorithm()).isEqualTo("EC");
      assertThat(((ECPublicKey) keys.getPublic()).getParams().getOrder().bitLength())
          .isEqualTo(521);
      assertThat(keys.getPrivate().getFormat()).isEqualTo("PKCS#8");
    }

    @Test
    @DisplayName("generateP521 makes a different key each time")
    void generateP521MakesANewKeyEachTime() throws Exception {
      assertThat(EcKeys.generateP521().getPublic()).isNotEqualTo(EcKeys.generateP521().getPublic());
    }
  }
}
