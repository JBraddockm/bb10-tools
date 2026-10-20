package com.jbraddockm.link.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Scrub}: removal of secrets and device identifiers from text that is logged or
 * shown.
 */
@DisplayName("Scrub: takes secrets and identifiers out of what is logged or shown")
class ScrubTest {

  private static String given(String name) {
    return Resources.text(ScrubTest.class, "scrub--" + name + ".txt");
  }

  private static String expected(String name) {
    return Resources.text(ScrubTest.class, "scrub--" + name + "--expected.txt");
  }

  @Nested
  @DisplayName("secrets")
  class Secrets {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "cookie",
          "login-session-cookie",
          "challenge-answer",
          "cookie-echo",
          "share-password-attribute",
          "share-password-double-quotes",
          "share-password-element"
        })
    @DisplayName(
        "hides the value of a session cookie, an answer to a challenge, a cookie echo and a share"
            + " password")
    void hidesSecrets(String name) {
      assertThat(Scrub.secrets(given(name))).isEqualTo(expected(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"nothing-secret", "pin", "user-name"})
    @DisplayName("leaves alone what is not a secret, even if it is an identifier")
    void leavesTheRest(String name) {
      var text = given(name);

      assertThat(Scrub.secrets(text)).isEqualTo(text);
    }
  }

  @Nested
  @DisplayName("identifiers")
  class Identifiers {

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "pin",
          "identifier-with-attributes",
          "imei",
          "imei-capitals",
          "chip-identifiers",
          "other-identifier-elements",
          "hardware-id",
          "device-name"
        })
    @DisplayName(
        "hides the content of the elements that identify a device, in any case, with attributes")
    void hidesIdentifiers(String name) {
      assertThat(Scrub.identifiers(given(name))).isEqualTo(expected(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"certificate-name", "certificate-name-short-pin"})
    @DisplayName("hides the PIN in the name of a client certificate, whatever its length")
    void hidesThePinInACertificateName(String name) {
      assertThat(Scrub.identifiers(given(name))).isEqualTo(expected(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {"empty-identifier", "user-name", "nothing-secret", "brackets-elsewhere"})
    @DisplayName(
        "leaves alone an empty element, elements that do not identify a device, and text in"
            + " brackets that is not the name of a certificate")
    void leavesTheRest(String name) {
      assertThat(Scrub.identifiers(given(name))).isEqualTo(expected(name));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cookie", "share-password-element", "identifier-and-secret"})
    @DisplayName("hides the secrets as well")
    void hidesSecretsAsWell(String name) {
      assertThat(Scrub.identifiers(given(name))).isEqualTo(expected(name));
    }
  }

  @Nested
  @DisplayName("forDisplay")
  class ForDisplay {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"display-cookie-echo", "display-plain", "display-only-cookie"})
    @DisplayName("takes the echoed cookie out of a text, and the blanks around what is left")
    void takesTheCookieOut(String name) {
      assertThat(Scrub.forDisplay(given(name))).isEqualTo(expected(name).strip());
    }
  }
}
