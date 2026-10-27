package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link Authentication}: the ways a session proves who asks and what each of them
 * requires.
 */
@DisplayName("Authentication: how a session proves who asks")
class AuthenticationTest {

  private static final ClientCertificate CERTIFICATE =
      new ClientCertificate(Path.of("client_cert.pem"), Path.of("client_key.pem"));

  private static final Authentication NONE = new Authentication.NoPassword();
  private static final Authentication PASSWORD = new Authentication.Password("secret");
  private static final Authentication CERTIFICATE_ALONE =
      new Authentication.Certificate(CERTIFICATE);
  private static final Authentication BOTH =
      new Authentication.CertificateAndPassword(CERTIFICATE, "secret");

  @Nested
  @DisplayName("what each kind is")
  class Kinds {

    @Test
    @DisplayName("type names the kind")
    void typeNamesTheKind() {
      assertThat(NONE.type()).isEqualTo(AuthenticationType.NONE);
      assertThat(PASSWORD.type()).isEqualTo(AuthenticationType.PASSWORD);
      assertThat(CERTIFICATE_ALONE.type()).isEqualTo(AuthenticationType.CERTIFICATE);
      assertThat(BOTH.type()).isEqualTo(AuthenticationType.CERTIFICATE_AND_PASSWORD);
    }

    @Test
    @DisplayName(
        "certificateAlone is true for a certificate with no password, and for nothing else")
    void certificateAlone() {
      assertThat(CERTIFICATE_ALONE.certificateAlone()).isTrue();
      assertThat(NONE.certificateAlone()).isFalse();
      assertThat(PASSWORD.certificateAlone()).isFalse();
      assertThat(BOTH.certificateAlone()).isFalse();
    }
  }

  @Nested
  @DisplayName("what each kind offers")
  class Offers {

    @Test
    @DisplayName(
        "certificateToOffer is the certificate of a kind that has one, and nothing for the others")
    void certificateToOffer() {
      assertThat(CERTIFICATE_ALONE.certificateToOffer()).contains(CERTIFICATE);
      assertThat(BOTH.certificateToOffer()).contains(CERTIFICATE);
      assertThat(NONE.certificateToOffer()).isEmpty();
      assertThat(PASSWORD.certificateToOffer()).isEmpty();
    }

    @Test
    @DisplayName(
        "passwordForChallenge is the password of a kind that has one, and nothing for the others")
    void passwordForChallenge() {
      assertThat(PASSWORD.passwordForChallenge()).contains("secret");
      assertThat(BOTH.passwordForChallenge()).contains("secret");
      assertThat(NONE.passwordForChallenge()).isEmpty();
      assertThat(CERTIFICATE_ALONE.passwordForChallenge()).isEmpty();
    }
  }

  @Nested
  @DisplayName("what is written of each kind")
  class Writing {

    @Test
    @DisplayName("a password is never written: toString hides it")
    void aPasswordIsHidden() {
      assertThat(PASSWORD.toString()).isEqualTo("Password[***]").doesNotContain("secret");
      assertThat(BOTH.toString())
          .startsWith("CertificateAndPassword[certificate=")
          .endsWith("password=***]")
          .doesNotContain("secret");
    }
  }

  @Nested
  @DisplayName("what is refused")
  class Refusals {

    @ParameterizedTest(name = "\"{0}\" is not a password")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName(
        "a password that is missing or blank, naming authentication.password, with or without a"
            + " certificate")
    void aBlankPassword(String password) {
      assertThatThrownBy(() -> new Authentication.Password(password))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("authentication.password"));
      assertThatThrownBy(() -> new Authentication.CertificateAndPassword(CERTIFICATE, password))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("authentication.password"));
    }

    @Test
    @DisplayName("a certificate that is missing")
    void aMissingCertificate() {
      assertThatNullPointerException()
          .isThrownBy(() -> new Authentication.Certificate(null))
          .withMessage("certificate");
      assertThatNullPointerException()
          .isThrownBy(() -> new Authentication.CertificateAndPassword(null, "secret"))
          .withMessage("certificate");
    }

    @Test
    @DisplayName("a client certificate needs both of its files")
    void aCertificateNeedsBothFiles() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ClientCertificate(null, Path.of("k")))
          .withMessage("certificateFile");
      assertThatNullPointerException()
          .isThrownBy(() -> new ClientCertificate(Path.of("c"), null))
          .withMessage("keyFile");
    }
  }
}
