package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Protocol-level tests for {@code connect}: refused arguments, authentication failures, session
 * details and login-only mode.
 */
@DisplayName("The daemon: connect")
class DaemonConnectTest {

  private FakeDevice device;
  private DaemonRun daemon;

  @BeforeEach
  void startTheDaemonAndTheDevice() throws Exception {
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

  private Map<String, Object> connect(long id, String resource, String password) throws Exception {
    daemon.send(id, resource, Map.of("port", String.valueOf(device.port()), "password", password));
    return daemon.done(id);
  }

  private Map<String, Object> connectWithoutPassword(long id, String resource) throws Exception {
    daemon.send(id, resource, Map.of("port", String.valueOf(device.port())));
    return daemon.done(id);
  }

  @Test
  @DisplayName("a session that was closed is unknown to the commands that come after")
  void aClosedSession() throws Exception {
    connect(6, "request--connect.json", "secret");
    var session = daemon.session(6);
    daemon.send(16, "request--disconnect.json", Map.of("session", session));
    daemon.done(16);

    daemon.send(
        17, "request--session-command.json", Map.of("method", "getPin", "session", session));

    assertThat(errorCode(daemon.done(17))).isEqualTo("UNKNOWN_SESSION");
  }

  @Nested
  @DisplayName("what is refused")
  class Refused {

    @Test
    @DisplayName("a connect with no host, naming host")
    void noHost() throws Exception {
      daemon.send(2, "request--connect-without-host.json");

      var done = daemon.done(2);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(done)).containsEntry("param", "host");
    }

    @Test
    @DisplayName("a port that is not from 1 to 65535, naming port")
    void aBadPort() throws Exception {
      daemon.send(3, "request--connect-port-zero.json");

      var done = daemon.done(3);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(done)).containsEntry("param", "port");
    }

    @Test
    @DisplayName(
        "the old top-level parameters, naming the one that moved and saying that it is part of"
            + " 'authentication' now")
    void theOldParameters() throws Exception {
      daemon.send(90, "request--connect-moved-certificate.json");
      daemon.send(91, "request--connect-moved-password.json");

      var certificate = daemon.done(90);
      var password = daemon.done(91);

      assertThat(errorCode(certificate)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(certificate)).containsEntry("param", "clientCertificate");
      assertThat(errorMessage(certificate)).contains("'authentication'");
      assertThat(errorDetails(password)).containsEntry("param", "password");
      assertThat(errorMessage(password)).contains("'authentication'");
    }

    @Test
    @DisplayName(
        "a connect with no authentication, with nothing assumed, and none of the refused requests"
            + " reached the device or made a login")
    void noAuthentication() throws Exception {
      daemon.send(
          5,
          "request--connect-without-authentication.json",
          Map.of("port", String.valueOf(device.port())));
      daemon.send(6, "request--connect-without-host.json");
      daemon.send(7, "request--connect-port-zero.json");

      var done = daemon.done(5);
      daemon.done(6);
      daemon.done(7);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(done)).containsEntry("param", "authentication");
      assertThat(device.loginRequests.get()).isZero();
      assertThat(device.cgiRequests.get()).isZero();
    }

    @Test
    @DisplayName("a deviceInfo that is not a boolean, naming deviceInfo")
    void deviceInfoMustBeABoolean() throws Exception {
      var done = connect(8, "request--connect-device-info-not-boolean.json", "secret");

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorDetails(done)).containsEntry("param", "deviceInfo");
    }
  }

  @Nested
  @DisplayName("over a Wi-Fi LAN address")
  class OverLan {

    @Test
    @DisplayName(
        "a full connect is refused before it dials, naming the method and the paths that reach it")
    void refusesAFullConnect() throws Exception {
      daemon.send(10, "request--connect-lan.json", Map.of("host", "192.168.1.118", "port", "8443"));

      var rejected = daemon.done(10);
      assertThat(errorCode(rejected)).isEqualTo("COMMAND_FAILED");
      assertThat(errorMessage(rejected))
          .contains("getDeviceProperties")
          .contains("Wi-Fi LAN")
          .contains("USB")
          .contains("tunnel");
      assertThat(daemon.events(10, "result")).isEmpty();
    }
  }

  @Nested
  @DisplayName("a wrong password")
  class WrongPassword {

    @Test
    @DisplayName(
        "is AUTH_FAILED with the count of failed attempts for the client, and there is no result of"
            + " the connect")
    void leavesNothingBehind() throws Exception {
      var rejected = connect(5, "request--connect.json", "wrong");

      assertThat(errorCode(rejected)).isEqualTo("AUTH_FAILED");
      assertThat(errorDetails(rejected)).containsEntry("failedAttempts", 1L);
      assertThat(daemon.events(5, "result")).isEmpty();
    }

    @Test
    @DisplayName("is still refused when the connect only logs in")
    void stillRefusedWhenOnlyLoggingIn() throws Exception {
      var rejected = connect(9, "request--connect-login-only.json", "wrong");

      assertThat(errorCode(rejected)).isEqualTo("AUTH_FAILED");
    }
  }

  @Nested
  @DisplayName("a connect that succeeds")
  class Succeeding {

    @Test
    @DisplayName(
        "says the session, the host, the port and how it authenticated, in this order, and echoes"
            + " the authentication")
    void saysWhatItDid() throws Exception {
      assertThat(connect(6, "request--connect.json", "secret").get("ok")).isEqualTo(true);

      var connected = daemon.data(6);
      assertThat(List.copyOf(connected.keySet()))
          .containsExactlyElementsOf(
              Resources.text(DaemonConnectTest.class, "connect--fields.txt").lines().toList());
      assertThat(connected)
          .containsEntry("host", "127.0.0.1")
          .containsEntry("authentication", "password");
      assertThat(connected.get("port")).isEqualTo((long) device.port());
    }

    @Test
    @DisplayName(
        "describes the device: its name, its PIN, and the versions of its system and its radio")
    void describesTheDevice() throws Exception {
      connect(6, "request--connect.json", "secret");

      var summary = object(daemon.data(6).get("device"));
      assertThat(summary)
          .containsEntry("name", "Test Z10")
          .containsEntry("pin", "2C1E7583")
          .containsEntry("guid", "RkFLRUdVSUQwMDAx")
          .containsEntry("osVersion", "10.3.3.2205")
          .containsEntry("radioVersion", "10.3.3.2206")
          .containsEntry("tunnelUp", true)
          .containsEntry("fuseEnabled", true);
    }

    @Test
    @DisplayName(
        "gives the credentials of the share of this login, with the NetBIOS name as the device"
            + " writes it")
    void givesTheShareCredentials() throws Exception {
      connect(6, "request--connect.json", "secret");

      var smb = object(daemon.data(6).get("smb"));
      assertThat(smb)
          .containsEntry("user", "dtm")
          .containsEntry("password", device.currentSharePassword())
          .containsEntry("workgroup", "WORKGROUP")
          .containsEntry("netBios", "%h")
          .containsEntry("host", "127.0.0.1");
    }

    @Test
    @DisplayName(
        "describes the request for the characteristics by what it does, and never names a program of"
            + " the device in the log")
    void logsWhatItDoes() throws Exception {
      connect(6, "request--connect.json", "secret");

      var messages =
          daemon.events(6, "log").stream()
              .map(event -> String.valueOf(event.get("message")))
              .toList();
      assertThat(messages).contains("Reading the device characteristics");
      assertThat(messages).noneMatch(message -> message.contains(".cgi"));
    }

    @Test
    @DisplayName(
        "never writes the password of the share in the log, nor the password of the login anywhere")
    void writesNoSecret() throws Exception {
      connect(6, "request--connect.json", "secret");

      var logged =
          daemon.events(6, "log").stream()
              .map(event -> String.valueOf(event.get("message")))
              .toList();
      assertThat(logged).noneMatch(message -> message.contains(FakeDevice.SHARE_PASSWORD_PREFIX));
      assertThat(daemon.output()).doesNotContain("\"secret\"");
    }
  }

  @Nested
  @DisplayName("a connect that goes another way")
  class OtherWays {

    @Test
    @DisplayName(
        "without a PIN that can be read still connects, with no PIN in the summary and a warning"
            + " that says why")
    void withoutAPin() throws Exception {
      device.discoveryXml = Resources.text(FakeDevice.class, "fake-device--discovery-error.xml");

      assertThat(connect(10, "request--connect.json", "secret").get("ok")).isEqualTo(true);

      assertThat(object(daemon.data(10).get("device")))
          .as("a PIN that is not known is left out, not written as null")
          .doesNotContainKey("pin")
          .containsEntry("name", "Test Z10");
      assertThat(daemon.events(10, "log"))
          .anyMatch(
              event ->
                  "warn".equals(event.get("level"))
                      && String.valueOf(event.get("message"))
                          .startsWith("Could not read the device characteristics"));
    }

    @Test
    @DisplayName("to a device that takes no password connects with type none, and echoes it")
    void aDeviceWithoutAPassword() throws Exception {
      device.loginMode = FakeDevice.LoginMode.NO_PASSWORD;

      assertThat(connectWithoutPassword(11, "request--connect-without-password.json").get("ok"))
          .isEqualTo(true);

      assertThat(daemon.data(11)).containsEntry("authentication", "none");
    }

    @Test
    @DisplayName(
        "with no password to a device that asks for one is PASSWORD_REQUIRED: the login stops at"
            + " the challenge and no attempt is counted")
    void aPasswordIsNeededAndNoneIsGiven() throws Exception {
      var done = connectWithoutPassword(12, "request--connect-without-password.json");

      assertThat(errorCode(done)).isEqualTo("PASSWORD_REQUIRED");
      assertThat(device.loginRequests.get()).isEqualTo(1);
      assertThat(device.failedAttempts).isZero();
    }
  }

  @Nested
  @DisplayName("a connect that only logs in")
  class LoggingInOnly {

    @Test
    @DisplayName(
        "says the fields without the device, gives the credentials of the share, and asks the"
            + " device nothing")
    void saysNothingOfTheDevice() throws Exception {
      assertThat(connect(13, "request--connect-login-only.json", "secret").get("ok"))
          .isEqualTo(true);

      var connected = daemon.data(13);
      assertThat(List.copyOf(connected.keySet()))
          .containsExactlyElementsOf(
              Resources.text(DaemonConnectTest.class, "connect--fields-login-only.txt")
                  .lines()
                  .toList());
      assertThat(connected)
          .as("what the device says of itself is left out, not written as null")
          .doesNotContainKey("device");
      assertThat(object(connected.get("smb")))
          .containsEntry("password", device.currentSharePassword());
      assertThat(device.cgiRequests.get())
          .as("no program of the device was asked anything")
          .isZero();
    }

    @Test
    @DisplayName(
        "gives a session that works afterwards, which asks the device when it is asked, and that"
            + " can be closed")
    void theSessionWorksAfterwards() throws Exception {
      connect(13, "request--connect-login-only.json", "secret");
      var session = daemon.session(13);

      daemon.send(
          14, "request--session-command.json", Map.of("method", "getPin", "session", session));
      assertThat(daemon.done(14).get("ok")).isEqualTo(true);
      assertThat(device.cgiRequests.get()).isEqualTo(1);

      daemon.send(15, "request--disconnect.json", Map.of("session", session));
      assertThat(daemon.done(15).get("ok")).isEqualTo(true);
    }
  }
}
