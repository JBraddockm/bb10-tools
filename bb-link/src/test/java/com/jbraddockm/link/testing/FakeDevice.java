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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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

  /**
   * What a real device answers to pimsync without a session; not valid JSON, {@code data} is not
   * quoted.
   */
  public static final String UNAUTHENTICATED_PIM_SYNC =
      "{\"version\":\"1.0\",\"status\":{\"code\":1013,\"msg\":\"received HTTP 401/403 from"
          + " server\"},\"data\":DTM NOT LOGGED IN}";

  public static final String DEFAULT_PIN =
      "<RimTabletResponse><DevicePIN><Status>Success</Status><PIN>PINX2A3B4C5D</PIN><Log></Log></DevicePIN></RimTabletResponse>";

  private final HttpServer server;
  private final String password;
  private final List<String> cgiBodies = Collections.synchronizedList(new ArrayList<>());
  private final List<String> cookiesSeen = Collections.synchronizedList(new ArrayList<>());
  private final List<String> pimRequests = Collections.synchronizedList(new ArrayList<>());

  /**
   * What pairing.cgi answers to a request with a valid session; by default it judges the
   * certificate.
   */
  public volatile String pairingAnswer;

  private final List<String> pairingBodies = Collections.synchronizedList(new ArrayList<>());

  /** What pimsync answers to a request with a valid session, by command; the default otherwise. */
  public final Map<String, String> pimResponses = new ConcurrentHashMap<>();

  /** What a real device answers to pairing.cgi without a session. */
  public static final String UNAUTHENTICATED_PAIRING =
      """
      <RimTabletResponse>
        <Pairing>
          <Status>Error</Status>
          <Error>
            <ErrorIdentifier>6</ErrorIdentifier>
            <ErrorDescription>Error - authorization.</ErrorDescription>
          </Error>
        </Pairing>
      </RimTabletResponse>
      """;

  /** What a real device answers to dynamicProperties.cgi when the request has no valid session. */
  public static final String UNAUTHORIZED_PROPERTIES =
      """
      <RimTabletResponse>
        <DynamicProperties>
          <Status>Error</Status>
          <Error>
            <ErrorIdentifier>1011</ErrorIdentifier>
            <ErrorDescription>Error - authorization</ErrorDescription>
          </Error>
        </DynamicProperties>
      </RimTabletResponse>
      """;

  /** What a real device answers to a certificate that is not one (captured). */
  public static final String PAIRING_REJECTED =
      "<RimTabletResponse><Pairing><Status>Error</Status><Error><ErrorIdentifier>7</ErrorIdentifier><ErrorDescription>Error"
          + " - insert certificate"
          + " failed.</ErrorDescription></Error></Pairing></RimTabletResponse>";

  /** The shape of a successful pairing is not known; this is made up. */
  public static final String PAIRING_SUCCESS =
      "<RimTabletResponse><Pairing><Status>Success</Status></Pairing></RimTabletResponse>";

  /** The answer to a command that has no entry in {@link #pimResponses}. */
  public static final String DEFAULT_PIM_ANSWER =
      "{\"version\":\"1.0\",\"status\":{\"code\":0,\"msg\":\"OK\"},\"data\":[]}";

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

  /** Run once while the next CGI request is answered, before the answer is sent; then cleared. */
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
    server.createContext("/cgi-bin/pimsync.cgi", this::pimSync);
    server.createContext("/cgi-bin/pairing.cgi", this::pairing);
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
   * Returns the body of every request that reached pairing.cgi, even those that were not answered.
   *
   * @return a snapshot of the form bodies
   */
  public List<String> pairingBodies() {
    synchronized (pairingBodies) {
      return List.copyOf(pairingBodies);
    }
  }

  /**
   * Returns the method, path and query of every request to pimsync.
   *
   * @return a snapshot, for example {@code GET /cgi-bin/pimsync.cgi?command=list_accounts}
   */
  public List<String> pimRequests() {
    synchronized (pimRequests) {
      return List.copyOf(pimRequests);
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
    int status = 200;
    String answer;
    String renewal = null;
    if (alwaysRejectCookie || cookie == null || !cookie.contains("dtmauth=" + token)) {
      switch (staleStyle) {
        case EMPTY -> answer = "";
        case UNAUTHORIZED -> {
          status = 401;
          answer = "unauthorized";
        }
        case AUTH_CHALLENGE ->
            answer =
                "<RimTabletResponse><AuthChallenge><Status>PasswdChallenge</Status></AuthChallenge></RimTabletResponse>";
        default -> throw new IllegalStateException();
      }
    } else if (!"POST".equals(exchange.getRequestMethod()) || !expectedBody.equals(body)) {
      status = 400;
      answer = "unexpected request: " + exchange.getRequestMethod() + " " + body;
    } else {
      renewal = "dtmauth=" + token + "; " + (cgiCookie == CgiCookie.RENEW ? FUTURE : PAST);
      answer = expectedBody.equals(PIN_BODY) ? pinXml : propertiesXml;
    }
    fireHook();
    respond(exchange, status, answer, renewal);
  }

  /**
   * Runs the hook that a test set for "after the next request", once. It runs after the device has
   * decided what to answer and before the answer is sent: the client cannot send its next request
   * before it has this answer, so the hook has always run by then. Running it after the answer is
   * sent would race with that next request.
   */
  private void fireHook() {
    var callback = afterNextCgiRequest;
    if (callback != null) {
      afterNextCgiRequest = null;
      callback.run();
    }
  }

  /** pimsync: a GET with a command; a real device's answer when there is no valid session. */
  private void pimSync(HttpExchange exchange) throws IOException {
    cgiRequests.incrementAndGet();
    var query = exchange.getRequestURI().getRawQuery();
    var cookie = exchange.getRequestHeaders().getFirst("Cookie");
    pimRequests.add(
        exchange.getRequestMethod()
            + " "
            + exchange.getRequestURI().getPath()
            + (query == null ? "" : "?" + query));
    cookiesSeen.add(cookie == null ? "" : cookie);
    if (dropNext > 0) {
      dropNext--;
      exchange.close();
      return;
    }
    if (alwaysRejectCookie || cookie == null || !cookie.contains("dtmauth=" + token)) {
      respond(exchange, 200, UNAUTHENTICATED_PIM_SYNC, null, "application/json");
      return;
    }
    var command = commandOf(query);
    if (command == null) {
      respond(
          exchange, 200, "", null,
          null); // a real device answers a request without a command with nothing
    } else {
      respond(
          exchange,
          200,
          pimResponses.getOrDefault(command, DEFAULT_PIM_ANSWER),
          null,
          "application/json");
    }
  }

  /**
   * pairing: a POST with the certificate; a real device's answer when there is no valid session.
   */
  private void pairing(HttpExchange exchange) throws IOException {
    cgiRequests.incrementAndGet();
    var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.US_ASCII);
    var cookie = exchange.getRequestHeaders().getFirst("Cookie");
    pairingBodies.add(body);
    cookiesSeen.add(cookie == null ? "" : cookie);
    if (dropNext > 0) {
      dropNext--;
      exchange.close();
      return;
    }
    var answer =
        alwaysRejectCookie || cookie == null || !cookie.contains("dtmauth=" + token)
            ? UNAUTHENTICATED_PAIRING
            : pairingAnswer != null ? pairingAnswer : judge(body);
    fireHook();
    respond(exchange, 200, answer, null);
  }

  /** The device is described as rejecting only a certificate that does not parse. */
  private static String judge(String body) {
    for (var pair : body.split("&")) {
      if (pair.startsWith("certdata=")) {
        var certificate =
            java.net.URLDecoder.decode(
                pair.substring("certdata=".length()), StandardCharsets.UTF_8);
        return certificate.contains("-----BEGIN CERTIFICATE-----")
            ? PAIRING_SUCCESS
            : PAIRING_REJECTED;
      }
    }
    return PAIRING_REJECTED;
  }

  private static String commandOf(String query) {
    if (query == null) {
      return null;
    }
    for (var pair : query.split("&")) {
      if (pair.startsWith("command=")) {
        return pair.substring("command=".length());
      }
    }
    return null;
  }

  private static void respond(HttpExchange exchange, int status, String body, String setCookie)
      throws IOException {
    respond(exchange, status, body, setCookie, "text/xml");
  }

  private static void respond(
      HttpExchange exchange, int status, String body, String setCookie, String contentType)
      throws IOException {
    var bytes = body.getBytes(StandardCharsets.UTF_8);
    if (contentType != null) {
      exchange.getResponseHeaders().add("Content-Type", contentType);
    }
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
