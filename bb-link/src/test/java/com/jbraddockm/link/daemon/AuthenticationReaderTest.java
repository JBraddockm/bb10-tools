package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.session.Authentication;
import com.jbraddockm.link.session.ClientCertificate;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link AuthenticationReader}: reading and validating the {@code authentication} object
 * of a connect request.
 */
@DisplayName("AuthenticationReader: the authentication in a request to connect")
class AuthenticationReaderTest {

  private static Authentication read(String resource) {
    return AuthenticationReader.read(
        new RequestParams(
            (Map<?, ?>) Json.parse(Resources.text(AuthenticationReaderTest.class, resource))));
  }

  @Nested
  @DisplayName("what it reads")
  class Reading {

    @Test
    @DisplayName("none: no password and no certificate")
    void none() {
      assertThat(read("authentication--none.json")).isEqualTo(new Authentication.NoPassword());
    }

    @Test
    @DisplayName("password: the password of the request")
    void password() {
      assertThat(read("authentication--password.json"))
          .isEqualTo(new Authentication.Password("secret"));
    }

    @Test
    @DisplayName("certificate: the two files, as paths")
    void certificate() {
      assertThat(read("authentication--certificate.json"))
          .isEqualTo(
              new Authentication.Certificate(
                  new ClientCertificate(Path.of("client_cert.pem"), Path.of("client_key.pem"))));
    }

    @Test
    @DisplayName("certificateAndPassword: the two files and the password")
    void certificateAndPassword() {
      assertThat(read("authentication--certificate-and-password.json"))
          .isEqualTo(
              new Authentication.CertificateAndPassword(
                  new ClientCertificate(Path.of("client_cert.pem"), Path.of("client_key.pem")),
                  "secret"));
    }
  }

  @Nested
  @DisplayName("what it refuses")
  class Refusing {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
      "authentication--moved-password.json, password",
      "authentication--moved-certificate.json, clientCertificate",
      "authentication--moved-key.json, clientKey"
    })
    @DisplayName(
        "a field that has moved into authentication, naming the field and showing where it goes"
            + " now")
    void aFieldThatHasMoved(String resource, String parameter) {
      assertThatThrownBy(() -> read(resource))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
          .hasMessageContaining("is now part of 'authentication', for example");
    }

    @ParameterizedTest(name = "{0}: {1} {2}")
    @CsvSource({
      "authentication--missing-type.json, authentication.type, is required",
      "authentication--unknown-type.json, authentication.type,"
          + " none/password/certificate/certificateAndPassword",
      "authentication--type-not-a-string.json, authentication.type, must be a string",
      "authentication--none-with-password.json, authentication.password, is not accepted with the"
          + " type 'none'",
      "authentication--password-with-key.json, authentication.clientKey, is not accepted with the"
          + " type 'password'",
      "authentication--certificate-with-password.json, authentication.password, is not accepted"
          + " with the type 'certificate'",
      "authentication--certificate-without-key.json, authentication.clientKey, is required with the"
          + " type 'certificate'",
      "authentication--certificate-blank-path.json, authentication.clientCertificate, is required"
          + " with the type 'certificate'",
      "authentication--certificate-invalid-path.json, authentication.clientCertificate, is not a"
          + " valid path",
      "authentication--path-not-a-string.json, authentication.clientKey, must be a string",
      "authentication--password-not-a-string.json, authentication.password, must be a string",
      "authentication--password-missing.json, authentication.password, is required",
      "authentication--password-blank.json, authentication.password, must not be blank"
    })
    @DisplayName(
        "what is wrong inside authentication, naming authentication.<field> and saying why")
    void whatIsWrongInside(String resource, String parameter, String reason) {
      assertThatThrownBy(() -> read(resource))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
          .hasMessageContaining(
              reason.replace(
                  "none/password/certificate/certificateAndPassword",
                  "none, password, certificate, certificateAndPassword"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"authentication--missing-object.json", "authentication--not-an-object.json"})
    @DisplayName(
        "a request with no authentication object, or with one that is not an object, naming"
            + " authentication")
    void noObject(String resource) {
      assertThatThrownBy(() -> read(resource))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("authentication"));
    }
  }
}
