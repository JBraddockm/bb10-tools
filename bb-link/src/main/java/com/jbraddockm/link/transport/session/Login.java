package com.jbraddockm.link.transport.session;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.internal.Impossible;
import com.qnx.bbt.deploy.PwCrypto;
import java.io.IOException;
import java.util.Optional;
import org.apache.http.cookie.Cookie;
import org.apache.http.cookie.CookieOrigin;
import org.apache.http.cookie.MalformedCookieException;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.impl.cookie.BestMatchSpec;
import org.apache.http.message.BasicHeader;

/**
 * The login of the device's web interface, the same as in the original tool.
 *
 * <p>The device answers a first request with a challenge; the client answers with a hash of the
 * password, the device's salt and the challenge, and receives the {@code dtmauth} cookie. The
 * password itself is never sent.
 */
@SuppressWarnings(
    "deprecation") // deprecated only in newer HttpClient versions; the build pins 4.0.3
final class Login {

  private static final String LOGIN_PATH = "/cgi-bin/login.cgi";
  private static final String COOKIE_NAME = "dtmauth";

  private Login() {}

  /**
   * Logs in.
   *
   * @return the file share credentials of this login, if the device handed them out
   */
  static Optional<SmbCredentials> perform(CgiTransport transport)
      throws LoginException, IOException {
    var first = transport.get(LOGIN_PATH + "?request_version=1");
    var document = LoginDocument.of(first);
    return switch (document.status()) {
      case DENIED -> throw document.denial();
      case ERROR -> throw legacyLogin();
      case SUCCESS -> {
        acceptCookie(transport, first);
        yield document.smb();
      }
      case PASSWORD_CHALLENGE -> answerChallenge(transport, document);
    };
  }

  private static Optional<SmbCredentials> answerChallenge(
      CgiTransport transport, LoginDocument challenge) throws LoginException, IOException {
    var algorithm = challenge.integer(LoginKey.ALGORITHM);
    if (algorithm == 0) {
      throw legacyLogin();
    }
    var password = transport.target().authentication().passwordForChallenge();
    if (password.isEmpty()) {
      throw new PasswordRequiredException();
    }
    var answerData = hash(password.get(), algorithm, challenge);
    var answer = transport.get(LOGIN_PATH + "?challenge_data=" + answerData + "&request_version=1");
    var document = LoginDocument.of(answer);
    return switch (document.status()) {
      case DENIED -> throw document.denial();
      case SUCCESS -> {
        acceptCookie(transport, answer);
        yield document.smb();
      }
      case ERROR, PASSWORD_CHALLENGE ->
          throw new UnusableLoginResponseException(
              "Status error: the device did not accept the answer to its challenge");
    };
  }

  private static String hash(String password, int algorithm, LoginDocument challenge)
      throws LoginException {
    var salt = challenge.required(LoginKey.SALT);
    var iterationCount = challenge.integer(LoginKey.ITERATION_COUNT);
    var challengeText = challenge.required(LoginKey.CHALLENGE);
    try {
      return Impossible.call(
          () -> PwCrypto.hashAndEncode(password, algorithm, salt, iterationCount, challengeText),
          "a required message digest is missing");
    } catch (NumberFormatException _) {
      throw new UnusableLoginResponseException("The salt is not a hexadecimal number: " + salt);
    }
  }

  private static UnusableLoginResponseException legacyLogin() {
    return new UnusableLoginResponseException(
        "The device asked for the legacy login, which bb-link does not support");
  }

  /**
   * Makes sure the cookie jar holds the {@code dtmauth} cookie.
   *
   * <p>If the device's clock is ahead of the host's, HttpClient treats the cookie as expired and
   * drops it. The cookie header is then parsed again and the expiry removed, so the session works
   * anyway. Only if that fails too is the clock blamed.
   */
  private static void acceptCookie(CgiTransport transport, CgiResponse response)
      throws LoginException {
    if (response.setCookie() == null) {
      // A device that recognises the client certificate logs the client in without a password and
      // without issuing a cookie; the certificate then authenticates every request **[verified]**.
      // Without a certificate, a success that brings no cookie is not a login.
      if (transport.target().authentication().certificateToOffer().isPresent()) {
        return;
      }
      throw new InvalidPasswordException();
    }
    for (Cookie cookie : transport.cookies().getCookies()) {
      if (COOKIE_NAME.equals(cookie.getName())) {
        return;
      }
    }
    var target = transport.target();
    var origin = new CookieOrigin(target.host(), target.port(), "/", false);
    try {
      for (Cookie cookie :
          new BestMatchSpec().parse(new BasicHeader("Set-Cookie", response.setCookie()), origin)) {
        if (COOKIE_NAME.equals(cookie.getName()) && cookie instanceof BasicClientCookie basic) {
          basic.setExpiryDate(null);
          transport.cookies().addCookie(basic);
          return;
        }
      }
    } catch (MalformedCookieException _) {
      // falls through to the clock explanation
    }
    throw new ClockOutOfSyncException();
  }
}
