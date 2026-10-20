package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.actions.ConfirmedByPin;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.pairing.AuthenticationAnswer;
import com.jbraddockm.link.pairing.GetAuthenticationCertificate;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestAuthority;
import com.jbraddockm.link.testing.TestResult;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Tests for the recovery of {@link DeviceSession}: a forgotten session is renewed once, and a
 * dropped connection is retried only for commands that read.
 */
@DisplayName("DeviceSession: what it does when the device forgets it or drops a connection")
class DeviceSessionRenewalTest {

  /** A command that reads in two requests, so that a session can be lost between them. */
  private record TwoRequests() implements DeviceCommand<TestResult> {

    private static CgiRequest ask() {
      return CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES).echo(FormField.GET_DYNAMIC_PROPERTIES);
    }

    @Override
    public Conversation<TestResult> conversation() {
      return new Conversation<>() {
        private int answers;

        @Override
        public Step<TestResult> start() {
          return new Step.Send<>(ask());
        }

        @Override
        public Step<TestResult> onAnswer(
            CgiResponse response, Optional<ResponseEnvelope> envelope) {
          return ++answers == 1
              ? new Step.Send<>(ask())
              : new Step.Finish<>(new TestResult("done"));
        }
      };
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  private FakeDevice device;
  private DeviceSession session;

  @BeforeEach
  void startTheDevice() throws IOException {
    device = new FakeDevice("secret");
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                "127.0.0.1",
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")));
  }

  @AfterEach
  void closeEverything() {
    session.close();
    device.close();
  }

  @Nested
  @DisplayName("a session that the device forgot")
  class Forgotten {

    @ParameterizedTest(name = "forgotten as {0}")
    @EnumSource(FakeDevice.StaleStyle.class)
    @DisplayName(
        "is renewed before anything is sent, whichever way the device says that it forgot it, and"
            + " the renewal is logged")
    void isRenewed(FakeDevice.StaleStyle style) {
      device.staleStyle = style;
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      device.expireSessions();
      var logs = new Logs();

      succeeded(session.execute(new GetDeviceProperties(), logs));

      assertThat(device.successfulLogins.get()).isEqualTo(2);
      assertThat(logs.messages(LogLevel.DEBUG))
          .containsExactly("The device forgot the session; logging in again");
    }

    @Test
    @DisplayName(
        "is renewed once and not in a loop: a device that refuses the new login too is told"
            + " plainly, with one login more")
    void isNotRenewedInALoop() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      device.alwaysRejectCookie = true;

      var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(failure.error().message())
          .isEqualTo("The device rejected the session right after a new login");
      assertThat(device.successfulLogins.get()).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "that is lost in the middle of a command is not renewed, and the command is not repeated:"
            + " it says that the session ended")
    void lostInTheMiddle() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      var requestsBefore = device.cgiRequests.get();
      device.afterNextCgiRequest = device::expireSessions;

      var failure = failed(session.execute(new TwoRequests(), CommandListener.SILENT));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(failure.error().message())
          .isEqualTo("The device ended the session while the command was running");
      assertThat(device.cgiRequests.get() - requestsBefore).isEqualTo(2);
      assertThat(device.successfulLogins.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "before a command that changes the device is renewed before anything is sent: the PIN that"
            + " is read first meets it, the renewal is logged, and the request goes out once")
    void beforeACommandThatChangesTheDevice() throws Exception {
      device.authority = new TestAuthority();
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      device.expireSessions();
      var logs = new Logs();

      var outcome = succeeded(session.execute(confirmedRequest(), logs));

      assertThat(outcome.certificate()).isPresent();
      assertThat(logs.messages(LogLevel.DEBUG))
          .containsExactly("The device forgot the session; logging in again");
      assertThat(device.authenticationRequests()).as("the request was sent once").hasSize(1);
    }

    @Test
    @DisplayName(
        "between the PIN and the request ends the command, plainly, and the request that was sent"
            + " is not sent again")
    void betweenThePinAndTheRequest() throws Exception {
      device.authority = new TestAuthority();
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      device.afterNextCgiRequest = device::expireSessions;

      var outcome = failed(session.execute(confirmedRequest(), CommandListener.SILENT));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(outcome.error().message()).contains("while the command was running");
      assertThat(device.authenticationRequests()).as("the request was not repeated").hasSize(1);
    }

    @Test
    @DisplayName(
        "is not what ends a request on a dropped connection: CONNECTION_LOST after the PIN and one"
            + " request, and nothing more")
    void aConnectionDroppedOnTheRequest() throws Exception {
      device.authority = new TestAuthority();
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      var before = device.cgiRequests.get();
      device.afterNextCgiRequest = () -> device.dropNext = 1;

      var outcome = failed(session.execute(confirmedRequest(), CommandListener.SILENT));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.cgiRequests.get())
          .as("the PIN and one request, nothing more")
          .isEqualTo(before + 2);
      assertThat(device.authenticationRequests()).as("the request was not repeated").hasSize(1);
    }

    private ConfirmedByPin<AuthenticationAnswer> confirmedRequest() {
      return new ConfirmedByPin<>(
          new GetAuthenticationCertificate(
              Resources.text(DeviceSessionRenewalTest.class, "device-session--real-request.pem")),
          "2A3B4C5D");
    }
  }

  @Nested
  @DisplayName("a connection that is dropped")
  class Dropped {

    @Test
    @DisplayName(
        "is opened again for a command that only reads, once, and logged in again: the read goes"
            + " through")
    void aReadIsRepeated() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      device.dropNext = 1;
      var logs = new Logs();

      succeeded(session.execute(new GetDeviceProperties(), logs));

      assertThat(logs.messages(LogLevel.DEBUG))
          .containsExactly("The connection was closed; connecting again");
      assertThat(device.successfulLogins.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("is opened again only once: a second drop is a lost connection, told as such")
    void aReadIsRepeatedOnce() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      var before = device.cgiRequests.get();
      device.dropNext = 2;

      var failure = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.cgiRequests.get() - before).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "is not opened again for a command that changes the device: it is sent once, and the loss"
            + " is told")
    void aWriteIsNeverRepeated() {
      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      var before = device.cgiRequests.get();
      device.dropNext = 1;
      var request = Resources.text(DeviceSessionRenewalTest.class, "device-session--request.pem");

      var failure =
          failed(
              session.execute(new GetAuthenticationCertificate(request), CommandListener.SILENT));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.cgiRequests.get() - before).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("the cookie of a command's answer")
  class Cookies {

    @Test
    @DisplayName("that is renewed keeps the session: one login for two commands")
    void aRenewedCookie() {
      device.cgiCookie = FakeDevice.CgiCookie.RENEW;

      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      succeeded(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

      assertThat(device.successfulLogins.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "that is already over is dropped by the client, so each command renews the session, at the"
            + " price of one login more")
    void anExpiredCookie() {
      device.cgiCookie = FakeDevice.CgiCookie.EXPIRED;

      succeeded(session.execute(new GetPin(), CommandListener.SILENT));
      succeeded(session.execute(new GetDeviceProperties(), CommandListener.SILENT));

      assertThat(device.successfulLogins.get()).isEqualTo(2);
    }
  }
}
