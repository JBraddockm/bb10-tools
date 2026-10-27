package com.jbraddockm.link.transport.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.ScriptedServer;
import com.jbraddockm.link.testing.TestPki;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.CgiTransport;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.http.cookie.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the login against the fake device: challenge and password-less logins, rejected and
 * locked accounts, and unusable answers.
 */
@SuppressWarnings("deprecation")
@DisplayName("Login: how a session logs in to a device")
class LoginTest {

  @TempDir private Path folder;
  private FakeDevice device;
  private CgiTransport transport;

  @BeforeEach
  void startTheDevice() throws IOException {
    device = new FakeDevice("secret");
  }

  @AfterEach
  void closeEverything() {
    if (transport != null) {
      transport.close();
    }
    device.close();
  }

  private CgiTransport open(int port, Authentication authentication) throws IOException {
    transport =
        new CgiTransport(
            new DeviceTarget("127.0.0.1", port, TransportScheme.HTTP, authentication),
            ExchangeObserver.NONE);
    return transport;
  }

  private CgiTransport openDevice(Authentication authentication) throws IOException {
    return open(device.port(), authentication);
  }

  private ClientCertificate certificate() {
    return new ClientCertificate(
        Resources.copyTo(TestPki.class, "test-pki--leaf-certificate.pem", folder),
        Resources.copyTo(TestPki.class, "test-pki--leaf-key-pkcs8.pem", folder));
  }

  @Nested
  @DisplayName("with a password, against a device that checks it")
  class WithThePassword {

    @Test
    @DisplayName(
        "answers the challenge with the hash of the password: the device accepts it, and the"
            + " credentials of the share come back")
    void answersTheChallenge() throws Exception {
      var credentials = Login.perform(openDevice(new Authentication.Password("secret")));

      assertThat(credentials)
          .hasValueSatisfying(
              share -> {
                assertThat(share.user()).isEqualTo("dtm");
                assertThat(share.password()).isEqualTo(device.currentSharePassword());
                assertThat(share.workgroup()).isEqualTo("WORKGROUP");
                assertThat(share.netBios()).isEqualTo("%h");
              });
      assertThat(device.loginRequests.get()).isEqualTo(2);
      assertThat(device.successfulLogins.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("keeps the cookie of the session in the store of the transport")
    void keepsTheCookie() throws Exception {
      Login.perform(openDevice(new Authentication.Password("secret")));

      assertThat(transport.cookies().getCookies()).extracting(Cookie::getName).contains("dtmauth");
    }

    @Test
    @DisplayName("gives no credentials when the device offers none")
    void noCredentialsWhenNoneAreOffered() throws Exception {
      device.offersShare = false;

      assertThat(Login.perform(openDevice(new Authentication.Password("secret")))).isEmpty();
    }

    @Test
    @DisplayName(
        "a wrong password is rejected, with the attempts that have failed and those that remain")
    void aWrongPassword() {
      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.Password("wrong"))))
          .isInstanceOfSatisfying(
              PasswordRejectedException.class,
              rejected -> {
                assertThat(rejected.failedAttempts()).isEqualTo(1);
                assertThat(rejected.remainingAttempts()).isEqualTo(4);
                assertThat(rejected.isLocked()).isFalse();
              });
    }

    @Test
    @DisplayName("the last wrong password locks the device, and the rejection says so")
    void theLastWrongPassword() {
      device.failedAttempts = 4;

      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.Password("wrong"))))
          .isInstanceOfSatisfying(
              PasswordRejectedException.class,
              rejected -> {
                assertThat(rejected.failedAttempts()).isEqualTo(5);
                assertThat(rejected.isLocked()).isTrue();
              });
    }
  }

  @Nested
  @DisplayName("with no password to give")
  class WithoutAPassword {

    @Test
    @DisplayName(
        "a device that asks for a password is not answered when there is none: the question is an"
            + " error, and nothing is guessed")
    void noPasswordNoAnswer() {
      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.NoPassword())))
          .isInstanceOf(PasswordRequiredException.class);
      assertThat(device.loginRequests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("a certificate alone has no password for the challenge either")
    void aCertificateAloneHasNoPassword() {
      assertThatThrownBy(
              () -> Login.perform(openDevice(new Authentication.Certificate(certificate()))))
          .isInstanceOf(PasswordRequiredException.class);
      assertThat(device.loginRequests.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "a device that logs in without a password gives the credentials of the share at the first"
            + " request")
    void aDeviceThatNeedsNoPassword() throws Exception {
      device.loginMode = FakeDevice.LoginMode.NO_PASSWORD;

      var credentials = Login.perform(openDevice(new Authentication.NoPassword()));

      assertThat(credentials).isPresent();
      assertThat(device.loginRequests.get()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("the cookie")
  class TheCookie {

    @Test
    @DisplayName(
        "that comes with a date in the past is kept as the cookie of the session, with no end")
    void aCookieThatIsAlreadyOver() throws Exception {
      device.loginMode = FakeDevice.LoginMode.NO_PASSWORD;
      device.cookieStyle = FakeDevice.CookieStyle.EXPIRED;

      Login.perform(openDevice(new Authentication.NoPassword()));

      assertThat(transport.cookies().getCookies()).hasSize(1);
      assertThat(transport.cookies().getCookies().getFirst().getName()).isEqualTo("dtmauth");
      assertThat(transport.cookies().getCookies().getFirst().getExpiryDate()).isNull();
    }

    @Test
    @DisplayName(
        "that does not come at all is not a login when no certificate was offered: the password was"
            + " not valid")
    void noCookieWithoutACertificate() {
      device.cookieStyle = FakeDevice.CookieStyle.MISSING;

      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.Password("secret"))))
          .isInstanceOf(InvalidPasswordException.class);
    }

    @Test
    @DisplayName(
        "that does not come at all is a login when a certificate was offered: the certificate"
            + " carries the requests")
    void noCookieWithACertificate() throws Exception {
      device.cookieStyle = FakeDevice.CookieStyle.MISSING;

      var credentials =
          Login.perform(
              openDevice(new Authentication.CertificateAndPassword(certificate(), "secret")));

      assertThat(credentials).isPresent();
      assertThat(transport.cookies().getCookies())
          .extracting(Cookie::getName)
          .doesNotContain("dtmauth");
    }
  }

  @Nested
  @DisplayName("an answer that cannot be used")
  class Unusable {

    @Test
    @DisplayName("a page of maintenance in place of XML is refused, with what is wrong with it")
    void aPageOfMaintenance() {
      device.loginMode = FakeDevice.LoginMode.GARBAGE;

      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable -> assertThat(unusable.extra()).startsWith("No content or invalid xml"));
    }

    @Test
    @DisplayName("an answer of the legacy login is refused, as bb-link does not speak it")
    void theLegacyLogin() {
      device.loginMode = FakeDevice.LoginMode.LEGACY;

      assertThatThrownBy(() -> Login.perform(openDevice(new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable -> assertThat(unusable.extra()).contains("legacy login"));
    }
  }

  @Nested
  @DisplayName("what a device may answer that a fake one does not")
  class Scripted {

    private ScriptedServer server;

    @BeforeEach
    void startTheServer() throws IOException {
      server = new ScriptedServer();
    }

    @AfterEach
    void stopTheServer() {
      server.close();
    }

    private String resource(String name) {
      return Resources.text(LoginTest.class, name);
    }

    @Test
    @DisplayName(
        "asks first for the login, with the version of the request, and answers the challenge with"
            + " its data and the version")
    void asksAndAnswers() throws Exception {
      server.answer(200, resource("login-test--challenge.xml"));
      server.answer(
          200, resource("login-document--success.xml"), "Set-Cookie", "dtmauth=TOKEN; path=/");

      Login.perform(open(server.port(), new Authentication.Password("secret")));

      assertThat(server.requests().get(0).target())
          .isEqualTo("/cgi-bin/login.cgi?request_version=1");
      assertThat(server.requests().get(1).target())
          .startsWith("/cgi-bin/login.cgi?challenge_data=")
          .endsWith("&request_version=1");
    }

    @Test
    @DisplayName(
        "a denial at the first request is a rejected password, with its counts, and nothing more is"
            + " asked")
    void aDenialAtOnce() {
      server.answer(200, resource("login-document--denied.xml"));

      assertThatThrownBy(
              () -> Login.perform(open(server.port(), new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              PasswordRejectedException.class,
              rejected -> {
                assertThat(rejected.failedAttempts()).isEqualTo(2);
                assertThat(rejected.remainingAttempts()).isEqualTo(8);
              });
      assertThat(server.requests()).hasSize(1);
    }

    @Test
    @DisplayName("a status of error at the first request is the legacy login")
    void anErrorAtOnce() {
      server.answer(200, resource("login-test--error.xml"));

      assertThatThrownBy(
              () -> Login.perform(open(server.port(), new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable -> assertThat(unusable.extra()).contains("legacy login"));
    }

    @Test
    @DisplayName("a challenge of the algorithm 0 is the legacy login, and the password is not used")
    void algorithmZero() {
      server.answer(200, resource("login-test--challenge-algorithm-0.xml"));

      assertThatThrownBy(
              () -> Login.perform(open(server.port(), new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable -> assertThat(unusable.extra()).contains("legacy login"));
      assertThat(server.requests()).hasSize(1);
    }

    @Test
    @DisplayName("a salt that is not hexadecimal is refused, saying which")
    void aSaltThatIsNotHexadecimal() {
      server.answer(200, resource("login-test--challenge-bad-salt.xml"));

      assertThatThrownBy(
              () -> Login.perform(open(server.port(), new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable ->
                  assertThat(unusable.extra())
                      .isEqualTo("The salt is not a hexadecimal number: zz"));
    }

    @Test
    @DisplayName(
        "a challenge again after the answer means that the device did not accept the answer")
    void aChallengeAfterTheAnswer() {
      server.answer(200, resource("login-test--challenge.xml"));
      server.answer(200, resource("login-test--challenge.xml"));

      assertThatThrownBy(
              () -> Login.perform(open(server.port(), new Authentication.Password("secret"))))
          .isInstanceOfSatisfying(
              UnusableLoginResponseException.class,
              unusable ->
                  assertThat(unusable.extra())
                      .isEqualTo(
                          "Status error: the device did not accept the answer to its challenge"));
    }

    @Test
    @DisplayName(
        "a cookie that is not the cookie of the session means that the clocks differ, whoever is"
            + " right")
    void aCookieThatIsNotTheSessions() {
      server.answer(200, resource("login-document--success.xml"), "Set-Cookie", "other=1; path=/");

      assertThatThrownBy(() -> Login.perform(open(server.port(), new Authentication.NoPassword())))
          .isInstanceOf(ClockOutOfSyncException.class);
    }
  }
}
