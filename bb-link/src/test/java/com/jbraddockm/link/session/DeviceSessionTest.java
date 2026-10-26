package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.ScriptedServer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link DeviceSession}: lazy login, session reuse, the data kept from the login, and
 * error reporting.
 */
@DisplayName("DeviceSession: the life of a session with a device")
class DeviceSessionTest {

  private final List<String> trace = new ArrayList<>();
  private FakeDevice device;
  private DeviceSession session;

  private DeviceSession sessionFor(int port, Authentication authentication) {
    return DeviceSession.forTarget(
        new DeviceTarget("127.0.0.1", port, TransportScheme.HTTP, authentication),
        (line, _, _, _) -> trace.add(line));
  }

  @BeforeEach
  void startTheDevice() throws IOException {
    device = new FakeDevice("secret");
    session = sessionFor(device.port(), new Authentication.Password("secret"));
  }

  @AfterEach
  void closeEverything() {
    session.close();
    device.close();
  }

  @Nested
  @DisplayName("logging in")
  class LoggingIn {

    @Test
    @DisplayName("is not done until a command is run, and is done once for all of them")
    void oneLoginForAllTheCommands() {
      assertThat(device.loginRequests.get()).isZero();

      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      succeeded(session.execute(new GetDeviceProperties(), CommandListener.SILENT));
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(device.successfulLogins.get()).isEqualTo(1);
      assertThat(device.loginRequests.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("is shown in the trace of the exchanges, before the command that needed it")
    void isInTheTrace() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(trace).startsWith("GET /cgi-bin/login.cgi?request_version=1");
      assertThat(trace.get(1)).startsWith("GET /cgi-bin/login.cgi?challenge_data=");
      assertThat(trace).last().isEqualTo("POST /cgi-bin/wipe.cgi");
    }

    @Test
    @DisplayName("tells what each request does, to the listener of the command")
    void tellsWhatTheCommandDoes() {
      var logs = new Logs();

      succeeded(session.execute(new GetPin(), logs));

      assertThat(logs.messages(LogLevel.INFO)).contains("Reading the device PIN");
    }
  }

  @Nested
  @DisplayName("the credentials of the share")
  class Credentials {

    @Test
    @DisplayName("are not there before the first login")
    void noneBeforeTheFirstLogin() {
      assertThat(session.smbCredentials()).isEmpty();
    }

    @Test
    @DisplayName("are what the login brought, once there has been one")
    void whatTheLoginBrought() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(session.smbCredentials())
          .hasValueSatisfying(
              share -> {
                assertThat(share.user()).isEqualTo("dtm");
                assertThat(share.password()).isEqualTo(device.currentSharePassword());
              });
    }

    @Test
    @DisplayName("are none when the device offers none")
    void noneWhenNoneAreOffered() {
      device.offersShare = false;

      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(session.smbCredentials()).isEmpty();
    }

    @Test
    @DisplayName(
        "are the latest ones: a new login brings a new password and the session reports it")
    void aNewLoginBringsNewOnes() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      var first = session.smbCredentials().orElseThrow().password();

      device.expireSessions();
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(session.smbCredentials().orElseThrow().password())
          .isNotEqualTo(first)
          .isEqualTo(device.currentSharePassword());
    }
  }

  @Nested
  @DisplayName("what goes wrong")
  class GoingWrong {

    @Test
    @DisplayName("a wrong password is AUTH_FAILED with the attempts that have failed and remain")
    void aWrongPassword() {
      var wrong = sessionFor(device.port(), new Authentication.Password("wrong"));

      var failure = failed(wrong.execute(new GetPin(), CommandListener.SILENT));
      wrong.close();

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(failure.error().details())
          .containsEntry("failedAttempts", 1)
          .containsEntry("retriesRemaining", 4)
          .containsEntry("maxAttempts", 5);
      assertThat(failure.error().message())
          .isEqualTo("Authentication failed. There have been 1 of 5 failed attempts");
      assertThat(device.cgiRequests.get()).as("no command is sent after a failed login").isZero();
    }

    @Test
    @DisplayName("the last wrong password is AUTH_LOCKED")
    void aLockedDevice() {
      device.failedAttempts = 4;
      var wrong = sessionFor(device.port(), new Authentication.Password("wrong"));

      var failure = failed(wrong.execute(new GetPin(), CommandListener.SILENT));
      wrong.close();

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_LOCKED);
      assertThat(failure.error().details()).containsEntry("failedAttempts", 5);
      assertThat(failure.error().message()).contains("too many failed attempts (5)");
    }

    @Test
    @DisplayName(
        "a device that asks for a password when none was given is PASSWORD_REQUIRED, and no answer"
            + " is sent")
    void aPasswordThatWasNotGiven() {
      var none = sessionFor(device.port(), new Authentication.NoPassword());

      var failure = failed(none.execute(new GetPin(), CommandListener.SILENT));
      none.close();

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.PASSWORD_REQUIRED);
      assertThat(device.loginRequests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a device that cannot be reached is CONNECTION_FAILED, with the cause")
    void aDeviceThatCannotBeReached() throws Exception {
      var gone = new ScriptedServer();
      var port = gone.port();
      gone.close();
      var lonely = sessionFor(port, new Authentication.Password("secret"));

      var failure = failed(lonely.execute(new GetPin(), CommandListener.SILENT));
      lonely.close();

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.CONNECTION_FAILED);
      assertThat(failure.error().details()).containsEntry("cause", "refused");
    }

    @Test
    @DisplayName(
        "a login that failed is tried again by the next command, with a transport of its own")
    void aFailedLoginIsTriedAgain() {
      device.cookieStyle = FakeDevice.CookieStyle.MISSING;
      failed(session.execute(new GetPin(), CommandListener.SILENT));

      device.cookieStyle = FakeDevice.CookieStyle.NORMAL;
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(device.successfulLogins.get()).isEqualTo(2);
    }
  }

  @Nested
  @DisplayName("a cookie that arrives already over")
  class Cookies {

    @Test
    @DisplayName("is rescued by the login: the commands work, and the cookie is sent with them")
    void aCookieThatIsRescued() {
      device.cookieStyle = FakeDevice.CookieStyle.EXPIRED;

      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(device.cookiesSeen().getLast()).contains("dtmauth=");
    }
  }

  @Nested
  @DisplayName("what a session is")
  class Being {

    @Test
    @DisplayName("says where it goes")
    void saysWhereItGoes() {
      assertThat(session.target().host()).isEqualTo("127.0.0.1");
      assertThat(session.target().port()).isEqualTo(device.port());
    }

    @Test
    @DisplayName(
        "cannot be used when it is closed: it says so, as an internal error, and sends nothing")
    void cannotBeUsedWhenClosed() {
      session.close();

      var failure = failed(session.execute(new GetPin(), CommandListener.SILENT));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.INTERNAL);
      assertThat(failure.error().message()).contains("closed");
      assertThat(device.loginRequests.get()).isZero();
    }

    @Test
    @DisplayName("can be closed more than once")
    void canBeClosedTwice() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));

      session.close();
      session.close();

      assertThat(session.target()).isNotNull();
    }

    @Test
    @DisplayName(
        "has a sibling for another port, scheme and authentication, to the same host, that shares"
            + " the observer")
    void hasASibling() {
      var sibling =
          session.sibling(TransportScheme.HTTP, device.port(), new Authentication.NoPassword());
      try {
        assertThat(sibling.target().host()).isEqualTo(session.target().host());
        assertThat(sibling.target().authentication()).isEqualTo(new Authentication.NoPassword());
        failed(sibling.execute(new GetPin(), CommandListener.SILENT));
        assertThat(trace).isNotEmpty();
      } finally {
        sibling.close();
      }
    }

    @Test
    @DisplayName("can be made without an observer")
    void canBeMadeWithoutAnObserver() {
      var quiet =
          DeviceSession.forTarget(
              new DeviceTarget(
                  "127.0.0.1",
                  device.port(),
                  TransportScheme.HTTP,
                  new Authentication.Password("secret")));

      succeeded(quiet.execute(new GetPin(), CommandListener.SILENT));
      quiet.close();

      assertThat(trace).isEmpty();
    }
  }

  @Nested
  @DisplayName("the SMB share address")
  class TheSmbShareAddress {

    private DeviceSession tunnelSession(String tunnelSmbHost) {
      return DeviceSession.forTarget(
          new DeviceTarget(
              "fd32:5bd4:58d2:3549:b594:f946:addb:bd94",
              DeviceTarget.DEFAULT_PORT,
              TransportScheme.HTTPS,
              new Authentication.Password("secret")),
          ExchangeObserver.NONE,
          tunnelSmbHost);
    }

    @Test
    @DisplayName("over the tunnel defaults to 169.254.1.1 when none is given")
    void tunnelDefaultsToTheStableAddress() {
      assertThat(tunnelSession(null).smbHost()).isEqualTo("169.254.1.1");
    }

    @Test
    @DisplayName("over the tunnel uses the per-session inner IPv4 when given")
    void tunnelUsesTheGivenInnerAddress() {
      assertThat(tunnelSession("169.254.9.1").smbHost()).isEqualTo("169.254.9.1");
    }

    @Test
    @DisplayName("over USB is the connection address, ignoring any tunnel override")
    void usbIsTheConnectionAddress() {
      var usb =
          DeviceSession.forTarget(
              new DeviceTarget(
                  "169.254.0.1",
                  DeviceTarget.DEFAULT_PORT,
                  TransportScheme.HTTPS,
                  new Authentication.Password("secret")),
              ExchangeObserver.NONE,
              "169.254.9.1");

      assertThat(usb.smbHost()).isEqualTo("169.254.0.1");
    }
  }
}
