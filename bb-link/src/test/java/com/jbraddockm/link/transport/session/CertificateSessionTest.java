package com.jbraddockm.link.transport.session;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import com.jbraddockm.link.testing.TlsTestDevice;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import java.nio.file.Path;
import java.util.List;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for sessions that authenticate with a client certificate, against a TLS test device. */
@DisplayName("A session with a certificate: what the device sees and what it is told")
class CertificateSessionTest {

  @TempDir private Path folder;
  private TlsTestDevice device;
  private String subject;

  @BeforeEach
  void startTheDevice() throws Exception {
    device = new TlsTestDevice();
    subject =
        CertificateDetails.firstCertificate(
                Resources.text(TestPki.class, "test-pki--leaf-certificate.pem"))
            .getSubjectX500Principal()
            .getName();
  }

  @AfterEach
  void stopTheDevice() throws Exception {
    device.close();
  }

  private ClientCertificate files(String certificate, String key) {
    return new ClientCertificate(copy(certificate), copy(key));
  }

  private Path copy(String name) {
    var owner =
        CertificateSessionTest.class.getResource(name) != null
            ? CertificateSessionTest.class
            : TestPki.class;
    return Resources.copyTo(owner, name, folder);
  }

  private ClientCertificate pair() {
    return files("test-pki--leaf-certificate.pem", "test-pki--leaf-key-pkcs8.pem");
  }

  private DeviceSession sessionWith(Authentication authentication) {
    return DeviceSession.forTarget(
        new DeviceTarget("127.0.0.1", device.port(), TransportScheme.HTTPS, authentication));
  }

  private String nameOfDevice(DeviceSession session, Logs logs) {
    return succeeded(session.execute(new GetDeviceProperties(), logs)).deviceName();
  }

  @Nested
  @DisplayName("what the device sees")
  class Seen {

    @ParameterizedTest(name = "a key as {0}")
    @ValueSource(strings = {"test-pki--leaf-key-pkcs8.pem", "test-pki--leaf-key-pkcs1.pem"})
    @DisplayName(
        "the certificate, whatever form the key has: one login without a password, then the"
            + " command, the certificate on both")
    void theCertificateReachesTheDevice(String key) {
      try (var session =
          sessionWith(
              new Authentication.Certificate(files("test-pki--leaf-certificate.pem", key)))) {
        assertThat(nameOfDevice(session, new Logs())).isEqualTo(subject);

        assertThat(device.paths())
            .containsExactly(
                "/cgi-bin/login.cgi?request_version=1", "/cgi-bin/dynamicProperties.cgi");
        assertThat(device.peers()).containsExactly(subject, subject);
        assertThat(session.smbCredentials())
            .hasValueSatisfying(share -> assertThat(share.password()).isEqualTo("TLS-SHARE"));
      }
    }

    @Test
    @DisplayName(
        "no certificate when none is offered: a session with a password, or with none, offers"
            + " nothing")
    void noCertificateWhenNoneIsOffered() {
      try (var session = sessionWith(new Authentication.NoPassword())) {
        assertThat(nameOfDevice(session, new Logs())).isEqualTo("none");

        assertThat(device.peers()).containsExactly("none", "none");
      }
    }

    @Test
    @DisplayName(
        "a certificate and a password: the certificate is offered on every request, and the login"
            + " is done")
    void aCertificateAndAPassword() {
      try (var session =
          sessionWith(new Authentication.CertificateAndPassword(pair(), "any password"))) {
        assertThat(nameOfDevice(session, new Logs())).isEqualTo(subject);

        assertThat(device.peers()).containsExactly(subject, subject);
        assertThat(device.paths().getFirst()).startsWith("/cgi-bin/login.cgi");
      }
    }

    @Test
    @DisplayName(
        "a client that offers no certificate is not refused for that when the device rejects the"
            + " certificates it is shown")
    void nothingOfferedIsNothingRejected() {
      device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;

      try (var session = sessionWith(new Authentication.NoPassword())) {
        assertThat(nameOfDevice(session, new Logs())).isEqualTo("none");
      }
    }
  }

  @Nested
  @DisplayName("a login with no cookie")
  class WithoutACookie {

    @Test
    @DisplayName(
        "is a login when the certificate was offered, as a real device does it: the share"
            + " credentials come, and the certificate carries every request")
    void aCertificateHolderIsLoggedIn() {
      device.issuesLoginCookie = false;

      try (var session =
          sessionWith(new Authentication.CertificateAndPassword(pair(), "any password"))) {
        assertThat(nameOfDevice(session, new Logs())).isEqualTo(subject);

        assertThat(session.smbCredentials())
            .hasValueSatisfying(share -> assertThat(share.password()).isEqualTo("TLS-SHARE"));
        assertThat(device.peers()).containsExactly(subject, subject);
      }
    }

    @Test
    @DisplayName("is not a login without a certificate: the password was not valid")
    void withoutACertificateItIsNotALogin() {
      device.issuesLoginCookie = false;

      try (var session = sessionWith(new Authentication.Password("x"))) {
        var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

        assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
        assertThat(failure.error().message()).contains("invalid password");
      }
    }
  }

  @Nested
  @DisplayName("a device that asks for a password all the same")
  class AsksForAPassword {

    @Test
    @DisplayName(
        "is not answered when only the certificate was given: a warning, the commands work, and no"
            + " share credentials come")
    void thePasswordQuestionIsAWarning() {
      device.behaviour = TlsTestDevice.Behaviour.LOGIN_ASKS_FOR_PASSWORD;
      var logs = new Logs();

      try (var session = sessionWith(new Authentication.Certificate(pair()))) {
        assertThat(nameOfDevice(session, logs)).isEqualTo(subject);

        assertThat(logs.messages(LogLevel.WARN)).hasSize(1);
        assertThat(logs.messages(LogLevel.WARN).getFirst()).contains("asked for a password");
        assertThat(session.smbCredentials()).isEmpty();
        assertThat(device.paths()).noneMatch(path -> path.contains("challenge_data"));
      }
    }

    @Test
    @DisplayName(
        "is an error when no certificate was given either: PASSWORD_REQUIRED, and nothing carries"
            + " on")
    void withoutACertificateItIsAnError() {
      device.behaviour = TlsTestDevice.Behaviour.LOGIN_ASKS_FOR_PASSWORD;

      try (var session = sessionWith(new Authentication.NoPassword())) {
        assertThat(
                failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT))
                    .error()
                    .code())
            .isEqualTo(LinkErrorCode.PASSWORD_REQUIRED);
      }
    }
  }

  @Nested
  @DisplayName("a device that does not take the certificate")
  class Refusing {

    @Test
    @DisplayName(
        "that refuses everyone says so, in terms of the certificate: AUTH_FAILED, and no password"
            + " is ever sent")
    void aRefusedCertificate() {
      device.behaviour = TlsTestDevice.Behaviour.REFUSES_EVERYONE;

      try (var session = sessionWith(new Authentication.Certificate(pair()))) {
        var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

        assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
        assertThat(failure.error().message()).contains("did not accept the client certificate");
        assertThat(device.paths()).noneMatch(path -> path.contains("challenge_data"));
      }
    }

    @Test
    @DisplayName(
        "that ends the handshake for a certificate it did not issue is a failed or lost connection")
    void aHandshakeThatEnds() {
      device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;

      try (var session = sessionWith(new Authentication.Certificate(pair()))) {
        var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

        assertThat(failure.error().code())
            .isIn(CoreErrorCode.CONNECTION_FAILED, CoreErrorCode.CONNECTION_LOST);
      }
    }
  }

  @Nested
  @DisplayName("files that cannot be used")
  class BadFiles {

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource({
      "test-pki--leaf-key-encrypted.pem, clientKey, is encrypted",
      "test-pki--ec-key-sec1.pem, clientKey, openssl pkcs8",
      "test-pki--other-key-pkcs8.pem, clientKey, does not belong to the certificate",
      "certificate-session--junk.txt, clientKey, contains no private key"
    })
    @DisplayName(
        "a key that cannot be used is INVALID_ARGUMENT, names the parameter, says why, and nothing"
            + " was sent")
    void aKeyThatCannotBeUsed(String key, String parameter, String reason) {
      try (var session =
          sessionWith(
              new Authentication.Certificate(files("test-pki--leaf-certificate.pem", key)))) {
        var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

        assertThat(failure.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
        assertThat(failure.error().message()).contains(reason);
        assertThat(failure.error().details()).containsEntry("param", parameter);
        assertThat(device.paths()).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "a certificate file that is not a certificate is INVALID_ARGUMENT, naming"
            + " clientCertificate")
    void aJunkCertificate() {
      try (var session =
          sessionWith(
              new Authentication.Certificate(
                  files("certificate-session--junk.txt", "test-pki--leaf-key-pkcs8.pem")))) {
        var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

        assertThat(failure.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
        assertThat(failure.error().details()).containsEntry("param", "clientCertificate");
      }
    }

    @Test
    @DisplayName("a key file that is not there is IO_ERROR")
    void aMissingKeyFile() {
      var missing =
          new ClientCertificate(
              copy("test-pki--leaf-certificate.pem"), folder.resolve("missing.pem"));

      try (var session = sessionWith(new Authentication.Certificate(missing))) {
        assertThat(
                failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT))
                    .error()
                    .code())
            .isEqualTo(CoreErrorCode.IO_ERROR);
      }
    }
  }

  @Test
  @DisplayName(
      "a handshake that fails after a certificate was offered is told with the certificate in mind,"
          + " and without it, without")
  void theWordsOfAFailedHandshake() {
    var failures =
        List.of(
            new SSLPeerUnverifiedException("peer not authenticated"),
            new SSLHandshakeException("Received fatal alert: bad_certificate"));

    for (var failure : failures) {
      var offered = FailureClassifier.classify(failure, true);
      var plain = FailureClassifier.classify(failure, false);

      assertThat(offered.message())
          .contains("client certificate was offered")
          .contains(failure.getMessage());
      assertThat(offered.details())
          .containsEntry("cause", "tls")
          .containsEntry("certificate", true);
      assertThat(plain.message()).doesNotContain("client certificate was offered");
      assertThat(plain.details()).doesNotContainKey("certificate");
    }
  }
}
