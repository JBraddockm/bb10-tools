package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredPairing;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.JsonObjects;
import com.jbraddockm.link.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link LinkService}: service information, methods, and how a connect request becomes an
 * established session.
 */
@DisplayName("LinkService: the service of the daemon for BlackBerry devices")
class LinkServiceTest {

  private static final long REQUEST_ID = 7;
  private final ByteArrayOutputStream written = new ByteArrayOutputStream();
  private FakeDevice device;
  private LinkService service;
  private DeviceSession session;
  private Reply reply;

  private static RequestParams request(String resource) {
    return new RequestParams(
        (Map<?, ?>) Json.parse(Resources.text(LinkServiceTest.class, resource)));
  }

  private static Map<String, Object> data(Establishment establishment) {
    assertThat(establishment).isInstanceOf(Establishment.Established.class);
    return JsonObjects.object(((Establishment.Established) establishment).data());
  }

  @BeforeEach
  void startTheDevice() throws IOException {
    device = new FakeDevice("secret");
    service =
        new LinkService(
            TransportScheme.HTTP,
            _ -> {},
            ExchangeObserver.NONE,
            new Pairing(),
            new CertificateRetrieval());
    reply = Reply.standalone(new PrintStream(written, true, StandardCharsets.UTF_8), REQUEST_ID);
  }

  @AfterEach
  void closeEverything() {
    if (session != null) {
      session.close();
    }
    device.close();
  }

  private RequestParams connect(String resource) {
    var text =
        Resources.text(
            LinkServiceTest.class, resource, Map.of("port", String.valueOf(device.port())));
    return new RequestParams((Map<?, ?>) Json.parse(text));
  }

  private Establishment establish(String resource) {
    var params = connect(resource);
    session = service.open(params);
    return service.establish(session, params, reply);
  }

  @Nested
  @DisplayName("the paired device identifier read beside a tunnel certificate")
  class PairedDeviceId {

    @TempDir private Path folder;

    @Test
    @DisplayName("is the deviceId that the pairing gave itself")
    void fromThePairing() throws IOException {
      var cert = folder.resolve("tunnel_cert.pem");
      Files.writeString(cert, "unused");
      Files.writeString(
          folder.resolve(PairingDirectory.METADATA_FILE),
          new StoredPairing("019C197F3871C2F52B133252D06A4A96A3", "PC issuer X", Instant.EPOCH)
              .toJsonText());

      assertThat(LinkService.pairedDeviceId(cert)).contains("019C197F3871C2F52B133252D06A4A96A3");
    }

    @Test
    @DisplayName("is empty when no certificate path was passed")
    void withoutACertificate() {
      assertThat(LinkService.pairedDeviceId(null)).isEmpty();
    }

    @Test
    @DisplayName("is empty when there is no pairing beside the certificate")
    void withoutAPairing() {
      assertThat(LinkService.pairedDeviceId(folder.resolve("tunnel_cert.pem"))).isEmpty();
    }
  }

  private List<Map<String, Object>> events() {
    return written
        .toString(StandardCharsets.UTF_8)
        .lines()
        .map(line -> JsonObjects.object(Json.parse(line)))
        .toList();
  }

  @Nested
  @DisplayName("what it says of itself")
  class Itself {

    @Test
    @DisplayName("is the tool bb-link, with its version")
    void isBbLink() {
      var info = service.info();

      assertThat(info.tool()).isEqualTo("bb-link");
      assertThat(info.version()).isEqualTo(LinkService.VERSION).isEqualTo("0.1.0");
    }

    @Test
    @DisplayName("has a command for each method, in the order of the methods")
    void hasACommandForEachMethod() {
      assertThat(service.commands())
          .extracting(CommandSpec::wireName)
          .hasSize(18)
          .first()
          .isEqualTo("getDeviceProperties");
    }
  }

  @Nested
  @DisplayName("a request to connect")
  class Connecting {

    @Test
    @DisplayName(
        "becomes a session to the host and the port, over the scheme of the service, with the"
            + " authentication that was asked")
    void becomesASession() {
      session = service.open(connect("link-service--connect.json"));

      assertThat(session.target().host()).isEqualTo("127.0.0.1");
      assertThat(session.target().port()).isEqualTo(device.port());
      assertThat(session.target().scheme()).isEqualTo(TransportScheme.HTTP);
      assertThat(session.target().authentication())
          .isEqualTo(new Authentication.Password("secret"));
    }

    @Test
    @DisplayName("takes the usual port, 8443, when none is given")
    void takesTheUsualPort() {
      session = service.open(request("link-service--connect-without-port.json"));

      assertThat(session.target().port()).isEqualTo(8443);
      assertThat(session.target().authentication()).isEqualTo(new Authentication.NoPassword());
    }

    @Test
    @DisplayName(
        "opens the session without talking to the device: that is done when it is established")
    void doesNotTalkYet() {
      session = service.open(connect("link-service--connect.json"));

      assertThat(device.loginRequests.get()).isZero();
    }

    @Test
    @SuppressWarnings("resource") // open() throws before creating a session
    @DisplayName("is refused when it has no host, naming host")
    void needsAHost() {
      assertThatThrownBy(() -> service.open(request("link-service--connect-without-host.json")))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("host"));
    }

    @Test
    @SuppressWarnings("resource") // open() throws before creating a session
    @DisplayName(
        "is refused when deviceInfo is not a boolean, naming deviceInfo, with no session made")
    void deviceInfoMustBeABoolean() {
      assertThatThrownBy(
              () -> service.open(request("link-service--connect-device-info-not-boolean.json")))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("deviceInfo"));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
      "link-service--connect-port-zero.json",
      "link-service--connect-port-too-large.json"
    })
    @SuppressWarnings("resource") // open() throws before creating a session
    @DisplayName("is refused when the port is not from 1 to 65535, naming port")
    void needsAGoodPort(String resource) {
      assertThatThrownBy(() -> service.open(request(resource)))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("port"));
    }
  }

  @Nested
  @DisplayName("a session that is established")
  class Establishing {

    @Test
    @DisplayName(
        "says where it is, how it authenticated, what the device says of itself, with its PIN, and"
            + " the credentials of the share")
    void describesTheDevice() {
      var described = data(establish("link-service--connect.json"));

      assertThat(described.get("host")).isEqualTo("127.0.0.1");
      assertThat(described.get("port")).isEqualTo(device.port());
      assertThat(described.get("authentication")).isEqualTo("password");
      assertThat(JsonObjects.object(described.get("device")))
          .containsEntry("name", "Test Z10")
          .containsEntry("hardwareId", "0x0a0b0c0d")
          .containsEntry("pin", "2C1E7583")
          .containsEntry("guid", "RkFLRUdVSUQwMDAx")
          .containsEntry("osVersion", "10.3.3.2205")
          .containsEntry("radioVersion", "10.3.3.2206")
          .containsEntry("tunnelUp", true)
          .containsEntry("developerMode", true)
          .containsEntry("fuseEnabled", true)
          .containsEntry("hasPower", false);
      assertThat(JsonObjects.object(described.get("smb")))
          .containsEntry("user", "dtm")
          .containsEntry("password", device.currentSharePassword());
    }

    @Test
    @DisplayName("reads the properties and then discovery.cgi, and logs in once")
    void asksTheDeviceTwice() {
      establish("link-service--connect.json");

      assertThat(device.successfulLogins.get()).isEqualTo(1);
      assertThat(device.cgiRequests.get()).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "with deviceInfo false only logs in: it says nothing of the device, asks it nothing, and"
            + " still gives the credentials of the share")
    void onlyLogsIn() {
      var described = data(establish("link-service--connect-login-only.json"));

      assertThat(described.get("device")).isNull();
      assertThat(JsonObjects.object(described.get("smb"))).containsEntry("user", "dtm");
      assertThat(device.cgiRequests.get()).isZero();
      assertThat(device.successfulLogins.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "takes what to do from the parameters that it is given, which are those of the connect")
    void takesItsInstructionsFromTheParameters() {
      session = service.open(connect("link-service--connect.json"));

      var described =
          data(service.establish(session, connect("link-service--connect-login-only.json"), reply));

      assertThat(described.get("device")).isNull();
    }

    @Test
    @DisplayName(
        "is told so in a warning when discovery.cgi cannot be read, and goes on with no PIN")
    void characteristicsThatCannotBeRead() {
      device.discoveryXml = Resources.text(FakeDevice.class, "fake-device--discovery-error.xml");

      var described = data(establish("link-service--connect.json"));

      assertThat(JsonObjects.object(described.get("device")))
          .containsEntry("pin", null)
          .containsEntry("name", "Test Z10");
      var warnings =
          events().stream()
              .filter(
                  event ->
                      "log".equals(event.get("type"))
                          && LogLevel.WARN.wireName().equals(event.get("level")))
              .toList();
      assertThat(warnings).hasSize(1);
      assertThat(String.valueOf(warnings.getFirst().get("message")))
          .isEqualTo("Could not read the device characteristics: something failed");
      assertThat(warnings.getFirst().get("id")).isEqualTo(REQUEST_ID);
    }
  }

  @Nested
  @DisplayName("a session that is not established")
  class Rejected {

    @Test
    @DisplayName("is rejected with AUTH_FAILED when the password is wrong")
    void aWrongPassword() {
      var establishment = establish("link-service--connect-wrong-password.json");

      assertThat(establishment)
          .isInstanceOfSatisfying(
              Establishment.Rejected.class,
              rejected -> assertThat(rejected.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED));
    }

    @Test
    @DisplayName(
        "is rejected as AUTH_LOCKED when the wrong password was the last attempt that the device"
            + " allows")
    void aLockedDevice() {
      device.failedAttempts = 4;

      var establishment = establish("link-service--connect-wrong-password.json");

      assertThat(establishment)
          .isInstanceOfSatisfying(
              Establishment.Rejected.class,
              rejected -> assertThat(rejected.error().code()).isEqualTo(CoreErrorCode.AUTH_LOCKED));
    }

    @Test
    @DisplayName("is rejected with the error of the device when it cannot give its properties")
    void propertiesThatCannotBeRead() {
      device.propertiesXml = Resources.text(GetDeviceProperties.class, "properties--error.xml");

      var establishment = establish("link-service--connect.json");

      assertThat(establishment)
          .isInstanceOfSatisfying(
              Establishment.Rejected.class,
              rejected -> {
                assertThat(rejected.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
                assertThat(rejected.error().message()).isEqualTo("Error - something failed");
              });
    }
  }
}
