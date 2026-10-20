package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the transport schemes and authentication types of a session. */
@DisplayName(
    "TransportScheme and AuthenticationType: how a session is carried, and how it authenticates")
class TransportAndAuthenticationTypeTest {

  @Nested
  @DisplayName("TransportScheme")
  class Scheme {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"HTTPS, https", "HTTP, http"})
    @DisplayName("each scheme is the word that a URL starts with")
    void eachSchemeIsAUrlWord(TransportScheme scheme, String word) {
      assertThat(scheme.urlScheme()).isEqualTo(word);
    }
  }

  @Nested
  @DisplayName("AuthenticationType")
  class Type {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "NONE, none",
      "PASSWORD, password",
      "CERTIFICATE, certificate",
      "CERTIFICATE_AND_PASSWORD, certificateAndPassword"
    })
    @DisplayName(
        "each type has the name that the protocol of the daemon uses, and of finds it by that name")
    void eachTypeHasItsWireName(AuthenticationType type, String wireName) {
      assertThat(type.wireName()).isEqualTo(wireName);
      assertThat(AuthenticationType.of(wireName)).isEqualTo(type);
    }

    @ParameterizedTest(name = "\"{0}\" is not a type")
    @NullAndEmptySource
    @ValueSource(strings = {"Password", "PASSWORD", "certificate_and_password", "token"})
    @DisplayName(
        "of refuses a name that is not exactly one of them, naming authentication.type and listing"
            + " the others")
    void ofRefusesOtherNames(String name) {
      assertThatThrownBy(() -> AuthenticationType.of(name))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("authentication.type"))
          .hasMessageContaining("none, password, certificate, certificateAndPassword");
    }
  }
}
