package com.jbraddockm.link.testing;

import com.qnx.bbt.deploy.PwCrypto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A device's web interface as a local HTTP server: the challenge login and the two CGI programs
 * bb-link uses so far. The shapes of the answers are those of a real device (login challenge,
 * success with the file share credentials, denial, the cookies and their dates), taken from a
 * capture. Its behaviour can be bent to reproduce the situations the session must survive.
 */
public final class FakeDevice implements AutoCloseable {

  /** How the device answers the login. */
  public enum LoginMode {
    /** A challenge that must be answered with the password hash. */
    CHALLENGE,
    /** Success at once, as for a device without a password. */
    NO_PASSWORD,
    /** An HTML page. */
    GARBAGE,
    /** The status {@code Error}, which asks for the legacy login. */
    LEGACY
  }

  /** How the {@code Set-Cookie} header of a successful login looks. */
  public enum CookieStyle {
    /** A cookie that expires far in the future. */
    NORMAL,
    /** A cookie that expired long ago, as when the device's clock is behind the host's. */
    EXPIRED,
    /** No cookie at all. */
    MISSING
  }

  /** What the CGI programs do with the {@code dtmauth} cookie in their own answers. */
  public enum CgiCookie {
    /** Renew it with an expiry far in the future. */
    RENEW,
    /** Set it with an expiry in the past, which a client that honours dates throws away. */
    EXPIRED
  }

  /** How the CGI programs reject a request without a valid cookie. */
  public enum StaleStyle {
    /** Status 200 and an empty body, as a real device does with an unknown cookie. */
    EMPTY,
    /** HTTP 401. */
    UNAUTHORIZED,
    /** An {@code AuthChallenge} element. */
    AUTH_CHALLENGE
  }

  private static final String SALT = "4EB86E31B720EAFF";
  private static final int ITERATIONS = 6000;
  private static final String CHALLENGE =
      "What's the airspeed velocity of an unladen swallow?3Pfa94fK6iYorCvomr32L2K5h2al";
  private static final int MAX_ATTEMPTS = 5;
  private static final String FUTURE = "expires=Fri, 01-Jan-2100 00:00:00 GMT; path=/";
  private static final String PAST = "expires=Thu, 01-Jan-1970 00:00:10 GMT; path=/";

  /** The start of the file share password; the device appends the number of the login. */
  public static final String SHARE_PASSWORD_PREFIX = "FAKE-SHARE-PASSWORD-";

  public static final String PROPERTIES_BODY =
      "Get%20Dynamic%20Properties=Get%20Dynamic%20Properties";
  public static final String PIN_BODY = "wipe=getpin";

  public static final String DEFAULT_PROPERTIES =
      """
      <RimTabletResponse><DynamicProperties><Status>Success</Status>
      <Applications>
      <Application><Name>sys.os.fake</Name><Type>os</Type><PackageId>os1</PackageId><PackageVersion>10.3.3.2205</PackageVersion></Application>
      <Application><Name>sys.radio.fake</Name><Type>radio</Type><PackageId>radio1</PackageId><PackageVersion>10.3.3.2206</PackageVersion></Application>
      </Applications>
      <DeviceStatus><BatteryLevel>87</BatteryLevel><BoardSerialNumber>FAKESERIAL0001</BoardSerialNumber></DeviceStatus>
      <DeviceHardware><HardwareID>0x8500240a</HardwareID></DeviceHardware>
      <DeviceInformation><DeviceName>Test Z10</DeviceName></DeviceInformation>
      </DynamicProperties></RimTabletResponse>
      """;

  public static final String DEFAULT_PIN =
      "<RimTabletResponse><DevicePIN><Status>Success</Status><PIN>PINX2A3B4C5D</PIN><Log></Log></DevicePIN></RimTabletResponse>";

  private final HttpServer server;
  private final String password;
  private final List<String> cgiBodies = Collections.synchronizedList(new ArrayList<>());
  private final List<String> cookiesSeen = Collections.synchronizedList(new ArrayList<>());

  /** Counts requests to the login program. */
  public final AtomicInteger loginRequests = new AtomicInteger();

  /** Counts successful logins; the file share password changes with each one. */
  public final AtomicInteger successfulLogins = new AtomicInteger();

  /** Whether the login answer carries file share credentials. */
  public volatile boolean offersShare = true;

  /** Counts requests to the CGI programs, including rejected and dropped ones. */
  public final AtomicInteger cgiRequests = new AtomicInteger();

  public volatile LoginMode loginMode = LoginMode.CHALLENGE;
  public volatile CookieStyle cookieStyle = CookieStyle.NORMAL;
  public volatile CgiCookie cgiCookie = CgiCookie.RENEW;
  public volatile StaleStyle staleStyle = StaleStyle.EMPTY;
  public volatile String propertiesXml = DEFAULT_PROPERTIES;
  public volatile String pinXml = DEFAULT_PIN;
  public volatile int failedAttempts;

  /** The number of upcoming CGI requests to drop without an answer. */
  public volatile int dropNext;

  /** Run once after the next CGI request was answered; then cleared. */
  public volatile Runnable afterNextCgiRequest;

  /** Whether the device rejects every cookie, never accepting a session. */
  public volatile boolean alwaysRejectCookie;

  private volatile String token = "TOKEN-1";

  /**
   * Starts the device on a free local port.
   *
   * @param password the password the device expects
   * @throws IOException if the port cannot be opened
   */
  public FakeDevice(String password) throws IOException {
    this.password = password;
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(Executors.newCachedThreadPool());
    server.createContext("/cgi-bin/login.cgi", this::login);
    server.createContext(
        "/cgi-bin/dynamicProperties.cgi", exchange -> cgi(exchange, PROPERTIES_BODY));
    server.createContext("/cgi-bin/wipe.cgi", exchange -> cgi(exchange, PIN_BODY));
    server.start();
  }

  /**
   * Returns the port the device listens on.
   *
   * @return the TCP port
   */
  public int port() {
    return server.getAddress().getPort();
  }

  /** Makes the device forget every session it issued, as after a reboot or a timeout. */
  public void expireSessions() {
    token = "TOKEN-" + System.nanoTime();
  }

  /**
   * Returns the bodies of all CGI requests received.
   *
   * @return a snapshot
   */
  public List<String> cgiBodies() {
    synchronized (cgiBodies) {
      return List.copyOf(cgiBodies);
    }
  }

  /**
   * Returns the cookie header of every CGI request, empty if there was none.
   *
   * @return a snapshot
   */
  public List<String> cookiesSeen() {
    synchronized (cookiesSeen) {
      return List.copyOf(cookiesSeen);
    }
  }

  private void login(HttpExchange exchange) throws IOException {
    loginRequests.incrementAndGet();
    var query = exchange.getRequestURI().getRawQuery();
    switch (loginMode) {
      case GARBAGE -> respond(exchange, 200, "<html><body>maintenance", null);
      case LEGACY -> respond(exchange, 200, auth("Error", ""), null);
      case NO_PASSWORD -> respond(exchange, 200, success(), cookieHeader());
      case CHALLENGE -> {
        if (query != null && query.contains("challenge_data=")) {
          answer(exchange, query);
        } else {
          respond(
              exchange,
              200,
              """
              <RimTabletResponse>
                <AuthChallenge>
                  <Status>PasswdChallenge</Status>
                  <Challenge>%s</Challenge>
                  <Algorithm>2</Algorithm>
                  <Salt>%s</Salt>
                  <ICount>%d</ICount>
                  <FailedAttempts>%d</FailedAttempts>
                  <RetriesRemaining>%d</RetriesRemaining>
                </AuthChallenge>
              </RimTabletResponse>
              """
                  .formatted(
                      CHALLENGE, SALT, ITERATIONS, failedAttempts, MAX_ATTEMPTS - failedAttempts),
              "loginsession=PRELOGIN-TOKEN; expires=Fri, 01-Jan-2100 00:00:00 GMT; path=/");
        }
      }
    }
  }

  private void answer(HttpExchange exchange, String query) throws IOException {
    String expected;
    try {
      expected = PwCrypto.hashAndEncode(password, 2, SALT, ITERATIONS, CHALLENGE);
    } catch (NoSuchAlgorithmException failure) {
      throw new IOException(failure);
    }
    if (query.contains("challenge_data=" + expected)) {
      respond(exchange, 200, success(), cookieHeader());
    } else {
      failedAttempts++;
      respond(
          exchange,
          200,
          auth(
              "Denied",
              "<FailedAttempts>%d</FailedAttempts><RetriesRemaining>%d</RetriesRemaining>"
                  .formatted(failedAttempts, MAX_ATTEMPTS - failedAttempts)),
          null);
    }
  }

  private String cookieHeader() {
    return switch (cookieStyle) {
      case NORMAL -> "dtmauth=" + token + "; " + FUTURE;
      case EXPIRED -> "dtmauth=" + token + "; " + PAST;
      case MISSING -> null;
    };
  }

  private static String auth(String status, String more) {
    return "<RimTabletResponse><Auth><Status>"
        + status
        + "</Status>"
        + more
        + "</Auth></RimTabletResponse>";
  }

  /**
   * Returns the password the device hands out with the latest successful login.
   *
   * @return the file share password
   */
  public String currentSharePassword() {
    return SHARE_PASSWORD_PREFIX + successfulLogins.get();
  }

  /** A real device hands the password of its file share to every client that logs in. */
  private String success() {
    var sharePassword = SHARE_PASSWORD_PREFIX + successfulLogins.incrementAndGet();
    return auth(
        "Success",
        offersShare
            ? "<Smb><Credentials User='dtm' Password='%1$s'/><User>dtm</User><Pwd>%1$s</Pwd>"
                    .formatted(sharePassword)
                + "<Workgroup>WORKGROUP</Workgroup><NetBios>%h</NetBios></Smb>"
            : "");
  }

  private void cgi(HttpExchange exchange, String expectedBody) throws IOException {
    cgiRequests.incrementAndGet();
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.US_ASCII);
    var cookie = exchange.getRequestHeaders().getFirst("Cookie");
    cgiBodies.add(body);
    cookiesSeen.add(cookie == null ? "" : cookie);
    if (dropNext > 0) {
      dropNext--;
      exchange.close();
      return;
    }
    if (alwaysRejectCookie || cookie == null || !cookie.contains("dtmauth=" + token)) {
      switch (staleStyle) {
        case EMPTY -> respond(exchange, 200, "", null);
        case UNAUTHORIZED -> respond(exchange, 401, "unauthorized", null);
        case AUTH_CHALLENGE ->
            respond(
                exchange,
                200,
                "<RimTabletResponse><AuthChallenge><Status>PasswdChallenge</Status></AuthChallenge></RimTabletResponse>",
                null);
      }
    } else if (!"POST".equals(exchange.getRequestMethod()) || !expectedBody.equals(body)) {
      respond(
          exchange, 400, "unexpected request: " + exchange.getRequestMethod() + " " + body, null);
    } else {
      var renewal = "dtmauth=" + token + "; " + (cgiCookie == CgiCookie.RENEW ? FUTURE : PAST);
      respond(exchange, 200, expectedBody.equals(PIN_BODY) ? pinXml : propertiesXml, renewal);
    }
    var callback = afterNextCgiRequest;
    if (callback != null) {
      afterNextCgiRequest = null;
      callback.run();
    }
  }

  private static void respond(HttpExchange exchange, int status, String body, String setCookie)
      throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/xml");
    if (setCookie != null) {
      exchange.getResponseHeaders().add("Set-Cookie", setCookie);
    }
    if (bytes.length == 0) {
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
      return;
    }
    exchange.sendResponseHeaders(status, bytes.length);
    try (var stream = exchange.getResponseBody()) {
      stream.write(bytes);
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
