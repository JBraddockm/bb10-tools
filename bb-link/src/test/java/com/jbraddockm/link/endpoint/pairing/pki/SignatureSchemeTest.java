package com.jbraddockm.link.endpoint.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link SignatureScheme}. */
@DisplayName("SignatureScheme: how a certificate or request says what it was signed with")
class SignatureSchemeTest {

  @ParameterizedTest(name = "{0} is {1}")
  @CsvSource({
    "SHA256_WITH_RSA, SHA256withRSA",
    "SHA1_WITH_RSA, SHA1withRSA",
    "SHA1_WITH_ECDSA, SHA1withECDSA"
  })
  @DisplayName("each scheme is the name that the JDK signs with")
  void eachSchemeHasItsJdkName(SignatureScheme scheme, String name) {
    assertThat(scheme.jcaName()).isEqualTo(name);
  }

  @ParameterizedTest(name = "{0} is written {1}")
  @CsvSource({
    "SHA256_WITH_RSA, 300D06092A864886F70D01010B0500",
    "SHA1_WITH_RSA, 300D06092A864886F70D0101050500",
    "SHA1_WITH_ECDSA, 300906072A8648CE3D0401"
  })
  @DisplayName(
      "algorithmIdentifier is the object identifier, with a NULL after it for RSA and nothing for"
          + " ECDSA")
  void algorithmIdentifierIsWhatTheStandardSays(SignatureScheme scheme, String expected) {
    assertThat(HexFormat.of().withUpperCase().formatHex(scheme.algorithmIdentifier()))
        .isEqualTo(expected);
  }
}
