package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.pairing.CertificateRetrieval;
import com.jbraddockm.link.pairing.Pairing;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.TestAuthority;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Protocol-level tests for {@code getAuthenticationCertificate}, {@code pairDevice} and {@code
 * inspectCertificate}.
 */
@DisplayName("The daemon: the certificate and the pairing")
class DaemonCertificateTest {

  private static final long CONNECT = 1;

  @TempDir private Path folder;
  private FakeDevice device;
  private DaemonRun daemon;
  private String session;

  @BeforeEach
  void connect() throws Exception {
    device = new FakeDevice("secret");
    device.authority = new TestAuthority();
    daemon =
        new DaemonRun(
            new LinkService(
                TransportScheme.HTTP,
                _ -> {},
                ExchangeObserver.NONE,
                new Pairing(),
                new CertificateRetrieval()));
    daemon.hello();
    daemon.send(
        CONNECT,
        "request--connect.json",
        Map.of("port", String.valueOf(device.port()), "password", "secret"));
    assertThat(daemon.done(CONNECT).get("ok")).isEqualTo(true);
    session = daemon.session(CONNECT);
  }

  @AfterEach
  void stopEverything() throws Exception {
    daemon.close();
    device.close();
  }

  private Map<String, Object> ask(long id, String resource) throws Exception {
    var values = new LinkedHashMap<String, String>();
    values.put("session", session);
    values.put("directory", folder.toString());
    daemon.send(id, resource, values);
    return daemon.done(id);
  }

  private Map<String, Object> askWithoutDirectory(long id, String resource) throws Exception {
    daemon.send(id, resource, Map.of("session", session));
    return daemon.done(id);
  }

  @Nested
  @DisplayName("getAuthenticationCertificate")
  class Certificate {

    @Test
    @DisplayName(
        "asks the device to sign a certificate: gives the common name of the request, the PIN, the"
            + " files that were kept, no key in the result, and says that the login was not tried"
            + " when it is switched off")
    void asksTheDeviceToSign() throws Exception {
      assertThat(ask(370, "request--get-authentication-certificate.json").get("ok"))
          .isEqualTo(true);

      var issued = daemon.data(370);
      assertThat(issued)
          .containsEntry("commonName", "BBCGIAUTH [2A3B4C5D] workstation")
          .containsEntry("pin", "2A3B4C5D");
      assertThat(Files.exists(Path.of((String) issued.get("certificateFile"))))
          .as("the certificate is kept")
          .isTrue();
      assertThat(Files.exists(Path.of((String) issued.get("keyFile")))).as("and the key").isTrue();
      assertThat(issued.toString()).as("and no key is in the result").doesNotContain("PRIVATE");
      assertThat(object(issued.get("verification"))).containsEntry("state", "skipped");
    }

    @Test
    @DisplayName(
        "tries the new certificate unless it is told not to: the identity is kept either way, and"
            + " the verification says that it was tried")
    void triesTheCertificateByDefault() throws Exception {
      assertThat(
              ask(375, "request--get-authentication-certificate-with-default-verification.json")
                  .get("ok"))
          .isEqualTo(true);

      var issued = daemon.data(375);
      assertThat(Files.exists(Path.of((String) issued.get("certificateFile")))).isTrue();
      assertThat(object(issued.get("verification")).get("state")).isIn("verified", "failed");
    }

    @Test
    @DisplayName("needs a folder, naming devicesDirectory")
    void needsAFolder() throws Exception {
      var done =
          askWithoutDirectory(
              371, "request--get-authentication-certificate-without-directory.json");

      assertThat(errorDetails(done)).containsEntry("param", "devicesDirectory");
    }

    @Test
    @DisplayName(
        "does not replace an identity that is there unless it is asked to: IDENTITY_EXISTS, and"
            + " then it does")
    void doesNotReplaceUnlessAsked() throws Exception {
      ask(370, "request--get-authentication-certificate.json");

      assertThat(errorCode(ask(372, "request--get-authentication-certificate.json")))
          .isEqualTo("IDENTITY_EXISTS");
      assertThat(ask(373, "request--get-authentication-certificate-replace.json").get("ok"))
          .isEqualTo(true);
    }

    @Test
    @DisplayName("refuses a name of the computer that is blank, naming hostName")
    void aBlankName() throws Exception {
      var done = ask(374, "request--get-authentication-certificate-blank-host.json");

      assertThat(errorDetails(done)).containsEntry("param", "hostName");
    }
  }

  @Nested
  @DisplayName("pairDevice")
  class PairingCommand {

    @Test
    @DisplayName(
        "pairs this computer with the device: gives the identifier, keeps the authority, its key"
            + " and the authority of the device, and describes both authorities, with no key in the"
            + " result")
    void pairsThisComputer() throws Exception {
      assertThat(ask(380, "request--pair-device.json").get("ok")).isEqualTo(true);

      var paired = daemon.data(380);
      assertThat(String.valueOf(paired.get("deviceId"))).matches("01[0-9A-F]{32}");
      assertThat(Files.exists(Path.of((String) paired.get("authorityFile"))))
          .as("the authority is kept")
          .isTrue();
      assertThat(Files.exists(Path.of((String) paired.get("keyFile")))).as("and its key").isTrue();
      assertThat(Files.exists(Path.of((String) paired.get("deviceAuthorityFile"))))
          .as("and the authority of the device")
          .isTrue();
      assertThat(paired.toString()).doesNotContain("PRIVATE");
      assertThat(object(paired.get("authority"))).containsEntry("certificateAuthority", true);
      assertThat(String.valueOf(object(paired.get("deviceAuthority")).get("subject")))
          .contains("CN=");
    }

    @Test
    @DisplayName("needs a folder, naming devicesDirectory")
    void needsAFolder() throws Exception {
      var done = askWithoutDirectory(381, "request--pair-device-without-directory.json");

      assertThat(errorDetails(done)).containsEntry("param", "devicesDirectory");
    }

    @Test
    @DisplayName(
        "does not replace a pairing that is there unless it is asked to: PAIRING_EXISTS, and then"
            + " it does")
    void doesNotReplaceUnlessAsked() throws Exception {
      ask(380, "request--pair-device.json");

      assertThat(errorCode(ask(382, "request--pair-device.json"))).isEqualTo("PAIRING_EXISTS");
      assertThat(ask(383, "request--pair-device-replace.json").get("ok")).isEqualTo(true);
    }
  }

  @Nested
  @DisplayName("inspectCertificate")
  class Inspecting {

    @Test
    @DisplayName(
        "needs no session: says the subject of a certificate, that 36 or 37 days are left of its"
            + " 37, and that it has not expired")
    void saysWhatTheCertificateSays() throws Exception {
      ask(370, "request--get-authentication-certificate.json");
      var file = String.valueOf(daemon.data(370).get("certificateFile"));

      daemon.send(336, "request--inspect-certificate.json", Map.of("file", file));

      assertThat(daemon.done(336).get("ok")).isEqualTo(true);
      var inspected = daemon.data(336);
      assertThat(String.valueOf(inspected.get("subject"))).contains("BBCGIAUTH");
      assertThat(((Number) inspected.get("daysLeft")).longValue()).isBetween(36L, 37L);
      assertThat(inspected).containsEntry("expired", false);
    }

    @Test
    @DisplayName("says that a file that is not there is an invalid argument, naming file")
    void aFileThatIsNotThere() throws Exception {
      daemon.send(
          337,
          "request--inspect-certificate.json",
          Map.of("file", folder.resolve("nothing.pem").toString()));

      var done = daemon.done(337);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(done)).containsEntry("param", "file");
    }
  }
}
