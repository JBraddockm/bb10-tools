package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Protocol-level tests for the {@code authentication} parameter of {@code connect}: invalid objects
 * and the certificate-based types.
 */
@DisplayName("The daemon: the authentication of a connect")
class DaemonAuthenticationTest {

  @TempDir private Path folder;
  private FakeDevice device;
  private DaemonRun daemon;

  @BeforeEach
  void startTheDaemon() throws Exception {
    device = new FakeDevice("secret");
    daemon =
        new DaemonRun(
            new LinkService(
                TransportScheme.HTTP,
                _ -> {},
                ExchangeObserver.NONE,
                new Pairing(),
                new CertificateRetrieval()));
    daemon.hello();
  }

  @AfterEach
  void stopEverything() throws Exception {
    daemon.close();
    device.close();
  }

  private Map<String, Object> connect(long id, String resource, Map<String, String> values)
      throws Exception {
    var all = new LinkedHashMap<>(values);
    all.put("port", String.valueOf(device.port()));
    daemon.send(id, resource, all);
    return daemon.done(id);
  }

  private Map<String, String> files() {
    return Map.of(
        "certificate",
            Resources.copyTo(TestPki.class, "test-pki--leaf-certificate.pem", folder).toString(),
        "key", Resources.copyTo(TestPki.class, "test-pki--leaf-key-pkcs8.pem", folder).toString());
  }

  @Nested
  @DisplayName("what is refused")
  class Refused {

    @ParameterizedTest(name = "{0} names {1}")
    @CsvFileSource(resources = "/com/jbraddockm/link/daemon/connect--invalid-authentications.csv")
    @DisplayName(
        "an authentication that is wrong in any of thirteen ways is INVALID_ARGUMENT, naming the"
            + " part that is wrong")
    void wrongInAnyWay(String resource, String parameter) throws Exception {
      var refusal = connect(100, resource, Map.of());

      assertThat(errorCode(refusal)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(refusal)).containsEntry("param", parameter);
    }

    @Test
    @DisplayName("none of the refused requests reached the device, and none made a login")
    void nothingReachedTheDevice() throws Exception {
      connect(100, "request--connect-invalid-authentication-02.json", Map.of());
      connect(101, "request--connect-invalid-authentication-09.json", Map.of());
      connect(102, "request--connect-without-authentication.json", Map.of());

      assertThat(device.cgiRequests.get()).isZero();
      assertThat(device.loginRequests.get()).isZero();
    }
  }

  @Nested
  @DisplayName("the types that use a certificate")
  class Certificates {

    @Test
    @DisplayName(
        "a certificate alone, to a device that does not take it, is AUTH_FAILED saying that it is"
            + " the certificate: it logs in twice, never answers the password question, counts no"
            + " attempt, and warns of the question")
    void aDeviceThatDoesNotTakeTheCertificate() throws Exception {
      var logins = device.loginRequests.get();

      var refusal = connect(152, "request--connect-certificate.json", files());

      assertThat(errorCode(refusal)).isEqualTo("AUTH_FAILED");
      assertThat(errorMessage(refusal)).contains("client certificate");
      assertThat(device.loginRequests.get()).isEqualTo(logins + 2);
      assertThat(device.failedAttempts.get()).isZero();
      assertThat(daemon.events(152, "log"))
          .anyMatch(
              event ->
                  "warn".equals(event.get("level"))
                      && String.valueOf(event.get("message")).contains("asked for a password"));
    }

    @Test
    @DisplayName(
        "a certificate and a password connects, echoes the type, and brings the credentials of the"
            + " share, since it logged in")
    void aCertificateAndAPassword() throws Exception {
      var logins = device.loginRequests.get();

      assertThat(connect(153, "request--connect-certificate-and-password.json", files()).get("ok"))
          .isEqualTo(true);

      assertThat(daemon.data(153))
          .containsEntry("authentication", "certificateAndPassword")
          .containsKey("smb");
      assertThat(device.loginRequests.get()).isGreaterThan(logins);
    }

    @Test
    @DisplayName("a certificate file that is not there is an error of reading")
    void aFileThatIsNotThere() throws Exception {
      var missing =
          Map.of(
              "certificate", folder.resolve("nothing.pem").toString(), "key", files().get("key"));

      assertThat(errorCode(connect(154, "request--connect-certificate.json", missing)))
          .isEqualTo("IO_ERROR");
    }

    @Test
    @DisplayName("the password of a connect never appears in what the daemon writes")
    void thePasswordNeverAppears() throws Exception {
      connect(153, "request--connect-certificate-and-password.json", files());

      assertThat(daemon.output()).doesNotContain("secret");
    }
  }
}
