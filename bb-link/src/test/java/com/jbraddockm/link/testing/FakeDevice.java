package com.jbraddockm.link.testing;

import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.CertificateRequest;
import com.jbraddockm.link.pairing.pki.RsaKeys;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import com.qnx.bbt.deploy.PwCrypto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates the web interface of a device as a local HTTP server: the challenge login and the CGI
 * programs that bb-link uses. Its behavior can be changed to reproduce failure conditions.
 */
public final class FakeDevice implements AutoCloseable {

  /** The start of the file share password; the device appends the number of the login. */
  public static final String SHARE_PASSWORD_PREFIX = "FAKE-SHARE-PASSWORD-";
  /** The form body that requests the properties of the device. */
  public static final String PROPERTIES_BODY =
      "Get%20Dynamic%20Properties=Get%20Dynamic%20Properties";
  /** The form body that requests the PIN of the device. */
  public static final String PIN_BODY = "wipe=getpin";
  /** The properties that the device answers by default. */
  public static final String DEFAULT_PROPERTIES =
      Resources.text(FakeDevice.class, "fake-device--properties.xml");
  /**
   * What a real device answers to pimsync without a session; not valid JSON, {@code data} is not
   * quoted.
   */
  public static final String UNAUTHENTICATED_PIM_SYNC =
      Resources.text(FakeDevice.class, "fake-device--pim-unauthenticated.json").strip();
  /** The PIN answer that the device gives by default. */
  public static final String DEFAULT_PIN = Resources.text(FakeDevice.class, "fake-device--pin.xml");
  /** The {@code discovery.cgi} answer that the device gives by default. */
  public static final String DEFAULT_DISCOVERY =
      Resources.text(FakeDevice.class, "fake-device--discovery.xml");
  /** What a real device answers to dynamicProperties.cgi when the request has no valid session. */
  public static final String UNAUTHORIZED_PROPERTIES =
      Resources.text(FakeDevice.class, "fake-device--properties-unauthorized.xml");
  /** The answer to a command that has no entry in {@link #pimResponses}. */
  public static final String DEFAULT_PIM_ANSWER =
      Resources.text(FakeDevice.class, "fake-device--pim-answer.json").strip();
  private static final String SALT = "4EB86E31B720EAFF";
  private static final int ITERATIONS = 6000;
  private static final String CHALLENGE =
      "What's the airspeed velocity of an unladen swallow?3Pfa94fK6iYorCvomr32L2K5h2al";
  private static final int MAX_ATTEMPTS = 5;
  private static final String FUTURE = "expires=Fri, 01-Jan-2100 00:00:00 GMT; path=/";
  private static final String PAST = "expires=Thu, 01-Jan-1970 00:00:10 GMT; path=/";
  /** A moment in the past, written as a device writes the date of a cookie. */
  private static final String EXPIRED =
      DateTimeFormatter.ofPattern("EEE, dd-MMM-yyyy HH:mm:ss 'GMT'", Locale.ENGLISH)
          .format(ZonedDateTime.now(ZoneOffset.UTC).minusYears(1));
  /** What pimsync answers to a request with a valid session, by command; the default otherwise. */
  public final Map<String, String> pimResponses = new ConcurrentHashMap<>();
  /** Counts requests to the login program. */
  public final AtomicInteger loginRequests = new AtomicInteger();
  /** Counts successful logins; the file share password changes with each one. */
  public final AtomicInteger successfulLogins = new AtomicInteger();
  /** Counts requests to the CGI programs, including rejected and dropped ones. */
  public final AtomicInteger cgiRequests = new AtomicInteger();
  /** The package ids that update.cgi refuses to remove. */
  public final Set<String> updateUnremovable = ConcurrentHashMap.newKeySet();
  /** What update.cgi was asked, one line per request, in order. */
  public final List<String> updateRequests = Collections.synchronizedList(new ArrayList<>());
  /** The user agent of every request to update.cgi. */
  public final List<String> updateAgents = Collections.synchronizedList(new ArrayList<>());
  private final HttpServer server;
  private final String password;
  private final List<String> cgiBodies = Collections.synchronizedList(new ArrayList<>());
  private final List<String> cookiesSeen = Collections.synchronizedList(new ArrayList<>());
  private final List<String> pimRequests = Collections.synchronizedList(new ArrayList<>());
  private final List<String> pairingBodies = Collections.synchronizedList(new ArrayList<>());
  private final Map<String, String> pairings = new ConcurrentHashMap<>();
  private final List<AuthenticationRequest> authenticationRequests =
      Collections.synchronizedList(new ArrayList<>());
  /**
   * When set, the device signs a certificate request that is posted to {@code login.cgi}, as the
   * real one does; when not, it answers that it cannot.
   */
  public volatile TestAuthority authority;
  /** When set, what a certificate request to {@code login.cgi} is answered, whatever it was. */
  public volatile String authenticationAnswer;
  /** When set, the certificate that the device signs is for another key than the request's. */
  public volatile boolean authenticationForAnotherKey;
  /** When set, what {@code pairing.cgi} answers a request that has a session, whatever it was. */
  public volatile String pairingAnswer;
  /** Whether the login answer carries file share credentials. */
  public volatile boolean offersShare = true;
  /** How the device answers the login. */
  public volatile LoginMode loginMode = LoginMode.CHALLENGE;
  /** How the cookie of a successful login is written. */
  public volatile CookieStyle cookieStyle = CookieStyle.NORMAL;
  /** What the CGI programs do with the session cookie in their answers. */
  public volatile CgiCookie cgiCookie = CgiCookie.RENEW;
  /** How the CGI programs reject a request without a valid session. */
  public volatile StaleStyle staleStyle = StaleStyle.EMPTY;
  /** The properties answer of the device. */
  public volatile String propertiesXml = DEFAULT_PROPERTIES;
  /** The PIN answer of the device. */
  public volatile String pinXml = DEFAULT_PIN;
  /** The {@code discovery.cgi} answer of the device. */
  public volatile String discoveryXml = DEFAULT_DISCOVERY;
  /** The number of failed password attempts that the login reports. */
  public volatile int failedAttempts;
  /** The number of upcoming CGI requests to drop without an answer. */
  public volatile int dropNext;
  /** Run once while the next CGI request is answered, before the answer is sent; then cleared. */
  public volatile Runnable afterNextCgiRequest;
  /** Whether the device rejects every cookie, never accepting a session. */
  public volatile boolean alwaysRejectCookie;
  /**
   * The percentages that update.cgi reports as {@code InProgress} for the successive polls of a
   * package; the next poll reports {@code Success}. A real device reports {@code InProgress} with
   * 100 once before {@code Success}.
   */
  public volatile List<Integer> updateProgress = List.of(3, 100);
  /** The status that update.cgi puts in {@code UpdateEnd}; empty for an end without a status. */
  public volatile String updateEndStatus = "Success";
  /**
   * The stage at which update.cgi fails: {@code start}, {@code send}, {@code progress}, {@code
   * finish}, or empty.
   */
  public volatile String updateFailsAt = "";
  /** If not negative, update.cgi drops the connection after reading this many upload bytes. */
  public volatile int updateDropUploadAfter = -1;
  /** Whether update.cgi takes a whole upload and then closes the connection without an answer. */
  public volatile boolean updateDropAfterUpload;
  /** If set, update.cgi answers every request with this text. */
  public volatile String updateAnswer;
  /**
   * The element that update.cgi answers the start of a removal with. A real device answers {@code
   * UpdateStart}.
   */
  public volatile String deleteStartElement = "UpdateStart";
  /**
   * The element that update.cgi answers every removal with. When {@code null}, the behavior of a
   * real device is used: {@code DeleteRequest} for the first removal of a conversation and {@code
   * DeleteProgress} for the others.
   */
  public volatile String deleteAnswerElement = null;
  /** The status that update.cgi gives in the answer to a removal. */
  public volatile String deleteAnswerStatus = "Success";
  /**
   * What update.cgi answers to the end of a removal: {@code UpdateEnd}, {@code empty} (nothing at
   * all), {@code DeleteProgress} or {@code Surprise}.
   */
  public volatile String deleteEnd = "UpdateEnd";
  /** If set, update.cgi answers every poll with this text. */
  public volatile String updatePollAnswer;
  private volatile int removalsSinceStart;
  private volatile int pollIndex;
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
    server.createContext("/cgi-bin/login.cgi", new LoginEndpoint()::handle);
    server.createContext("/cgi-bin/pairing.cgi", new PairingEndpoint()::handle);
    server.createContext(
        "/cgi-bin/dynamicProperties.cgi", exchange -> cgi(exchange, PROPERTIES_BODY));
    server.createContext("/cgi-bin/wipe.cgi", exchange -> cgi(exchange, PIN_BODY));
    server.createContext("/cgi-bin/discovery.cgi", this::discovery);
    server.createContext("/cgi-bin/pimsync.cgi", new PimSyncEndpoint()::handle);
    server.createContext("/cgi-bin/update.cgi", new UpdateEndpoint()::handle);
    server.start();
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

  /**
   * The bodies of the requests that reached {@code pairing.cgi}.
   *
   * @return a copy, in the order they came
   */
  public List<String> pairingBodies() {
    synchronized (pairingBodies) {
      return List.copyOf(pairingBodies);
    }
  }

  /**
   * The authorities that computers paired with the device, by the identifier that each gave itself.
   *
   * @return a copy
   */
  public Map<String, String> pairings() {
    return Map.copyOf(pairings);
  }

  /**
   * The certificate requests that reached {@code login.cgi}.
   *
   * @return a copy, in the order they came
   */
  public List<AuthenticationRequest> authenticationRequests() {
    synchronized (authenticationRequests) {
      return List.copyOf(authenticationRequests);
    }
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

  /**
   * Returns the password the device hands out with the latest successful login.
   *
   * @return the file share password
   */
  public String currentSharePassword() {
    return SHARE_PASSWORD_PREFIX + successfulLogins.get();
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
                Resources.text(FakeDevice.class, "fake-device--stale-auth-challenge.xml").strip();
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
   * Answers {@code discovery.cgi}, which is a GET (no body) rather than the POST form the others
   * use.
   */
  private void discovery(HttpExchange exchange) throws IOException {
    cgiRequests.incrementAndGet();
    var cookie = exchange.getRequestHeaders().getFirst("Cookie");
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
                Resources.text(FakeDevice.class, "fake-device--stale-auth-challenge.xml").strip();
        default -> throw new IllegalStateException();
      }
    } else if (!"GET".equals(exchange.getRequestMethod())) {
      status = 400;
      answer = "unexpected request: " + exchange.getRequestMethod();
    } else {
      renewal = "dtmauth=" + token + "; " + (cgiCookie == CgiCookie.RENEW ? FUTURE : PAST);
      answer = discoveryXml;
    }
    fireHook();
    respond(exchange, status, answer, renewal);
  }

  /**
   * Runs the hook set for "after the next request", once, after the answer is decided and before it
   * is sent. Running it later would race with the next request of the client.
   */
  private void fireHook() {
    var callback = afterNextCgiRequest;
    if (callback != null) {
      afterNextCgiRequest = null;
      callback.run();
    }
  }

  @Override
  public void close() {
    server.stop(0);
  }

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

  /**
   * A certificate request that reached {@code login.cgi}.
   *
   * @param query the query string
   * @param contentType the content type of the body
   * @param body the body
   */
  public record AuthenticationRequest(String query, String contentType, String body) {}

  /** Handles login requests. */
  final class LoginEndpoint {
    private static String auth(String status, String more) {
      return Resources.text(
              FakeDevice.class,
              "fake-device--login-auth.xml",
              Map.of("status", status, "more", more))
          .strip();
    }

    void handle(HttpExchange exchange) throws IOException {
      if ("POST".equals(exchange.getRequestMethod())) {
        new AuthenticationEndpoint().handle(exchange);
        return;
      }
      loginRequests.incrementAndGet();
      var query = exchange.getRequestURI().getRawQuery();
      switch (loginMode) {
        case GARBAGE ->
            respond(
                exchange,
                200,
                Resources.text(FakeDevice.class, "fake-device--login-maintenance.txt").strip(),
                null);
        case LEGACY -> respond(exchange, 200, auth("Error", ""), null);
        case NO_PASSWORD -> respond(exchange, 200, success(), cookieHeader());
        case CHALLENGE -> {
          if (query != null && query.contains("challenge_data=")) {
            answer(exchange, query);
          } else {
            respond(
                exchange,
                200,
                Resources.text(
                    FakeDevice.class,
                    "fake-device--login-challenge.xml",
                    Map.of(
                        "challenge", CHALLENGE,
                        "salt", SALT,
                        "iterations", String.valueOf(ITERATIONS),
                        "failedAttempts", String.valueOf(failedAttempts),
                        "retriesRemaining", String.valueOf(MAX_ATTEMPTS - failedAttempts))),
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
                Resources.text(
                        FakeDevice.class,
                        "fake-device--login-counts.xml",
                        Map.of(
                            "failedAttempts", String.valueOf(failedAttempts),
                            "retriesRemaining", String.valueOf(MAX_ATTEMPTS - failedAttempts)))
                    .strip()),
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

    /** A real device hands the password of its file share to every client that logs in. */
    private String success() {
      var sharePassword = SHARE_PASSWORD_PREFIX + successfulLogins.incrementAndGet();
      return auth(
          "Success",
          offersShare
              ? Resources.text(
                      FakeDevice.class,
                      "fake-device--login-share.xml",
                      Map.of("password", sharePassword))
                  .strip()
              : "");
    }
  }

  /** Handles pimsync requests. */
  final class PimSyncEndpoint {
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

    /** pimsync: a GET with a command; a real device's answer when there is no valid session. */
    void handle(HttpExchange exchange) throws IOException {
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
  }

  /** Handles update requests. */
  final class UpdateEndpoint {
    private static String updateAnswerOf(String element, String status, String extra) {
      return Resources.text(
              FakeDevice.class,
              "fake-device--update-answer.xml",
              Map.of("element", element, "status", status, "extra", extra))
          .strip();
    }

    private static String updateError(String element, int identifier, String description) {
      var details =
          Resources.text(
                  FakeDevice.class,
                  "fake-device--update-error-details.xml",
                  Map.of("identifier", String.valueOf(identifier), "description", description))
              .strip();
      return updateAnswerOf(element, "Error", details);
    }

    /**
     * Handles update.cgi: a conversation tracked by a cookie issued by the start, with a start (a
     * form with a mode and a size), uploads (octet-stream bodies, with a type in the query), polls
     * (an empty form) and a finish (status=success).
     */
    void handle(HttpExchange exchange) throws IOException {
      cgiRequests.incrementAndGet();
      var cookie = exchange.getRequestHeaders().getFirst("Cookie");
      var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      updateAgents.add(String.valueOf(exchange.getRequestHeaders().getFirst("User-Agent")));
      cookiesSeen.add(cookie == null ? "" : cookie);
      var query = exchange.getRequestURI().getRawQuery();
      var upload = contentType != null && contentType.startsWith("application/octet-stream");
      if (upload && updateDropUploadAfter >= 0) {
        var read = exchange.getRequestBody().readNBytes(updateDropUploadAfter);
        updateRequests.add("upload dropped after " + read.length + " bytes");
        exchange.close();
        return;
      }
      var body = exchange.getRequestBody().readAllBytes();
      if (upload && updateDropAfterUpload) {
        updateRequests.add("upload received, not answered: bytes=" + body.length);
        exchange.close();
        return;
      }
      if (dropNext > 0) {
        dropNext--;
        updateRequests.add("dropped, not answered");
        exchange.close();
        return;
      }
      var form = new String(body, StandardCharsets.US_ASCII);
      if (alwaysRejectCookie || cookie == null || !cookie.contains("dtmauth=" + token)) {
        updateRequests.add("no session");
        fireHook();
        respond(exchange, 200, "", null);
        return;
      }
      String answer;
      String setCookie = null;
      var failure = updateFailsAt;
      if (updateAnswer != null) {
        updateRequests.add("answered with the given text");
        answer = updateAnswer;
      } else if (upload) {
        updateRequests.add("upload " + query + " bytes=" + body.length + " type=" + contentType);
        if (!"application/octet-stream".equals(contentType)) {
          answer = updateError("UpdateSend", 9, "Expecting application/octet-stream");
        } else if (failure.equals("send")) {
          answer = updateError("UpdateSend", 12, "Cannot install bar file");
        } else {
          pollIndex = 0;
          answer = updateAnswerOf("UpdateSend", "Success", "");
        }
      } else if (form.contains("mode=")) {
        updateRequests.add("start " + form);
        if (!(form.contains("mode=bar") || form.contains("mode=os") || form.contains("mode=app"))) {
          answer =
              updateError(
                  "UpdateStart", 3, "Expecting 'mode' attribute set to one of: 'app','os','bar'");
        } else if (failure.equals("start")) {
          answer =
              updateError(
                  form.contains("mode=app") ? "DeleteRequest" : "UpdateStart",
                  4,
                  "unable to verify app manifest");
        } else {
          // The cookie arrives already expired, as on a real device, so a client never keeps it.
          setCookie = "update=" + System.nanoTime() + "; expires=" + EXPIRED + "; path=/";
          removalsSinceStart = 0;
          answer =
              updateAnswerOf(
                  form.contains("mode=app") ? deleteStartElement : "UpdateStart", "Ready", "");
        }
      } else if (form.contains("packageid=")) {
        var packageId = form.replaceAll(".*packageid=([^&]*).*", "$1");
        updateRequests.add("remove " + packageId);
        var element =
            deleteAnswerElement != null
                ? deleteAnswerElement
                : removalsSinceStart++ == 0 ? "DeleteRequest" : "DeleteProgress";
        answer =
            updateUnremovable.contains(packageId) || failure.equals("delete")
                ? updateError(element, 8, "Cannot uninstall packageid")
                : updateAnswerOf(element, deleteAnswerStatus, "");
      } else if (form.contains("status=success") && form.contains("type=bar")) {
        updateRequests.add("finish removal");
        if (failure.equals("finish")) {
          answer = updateError("UpdateEnd", 6, "Cannot finish");
        } else {
          answer =
              switch (deleteEnd) {
                case "empty" -> "";
                case "DeleteProgress" -> updateAnswerOf("DeleteProgress", "Success", "");
                case "Weird" -> updateAnswerOf("UpdateEnd", "Weird", "");
                case "Surprise" ->
                    Resources.text(FakeDevice.class, "fake-device--surprise.xml").strip();
                default -> updateAnswerOf("UpdateEnd", "Success", "");
              };
        }
      } else if (form.contains("status=success")) {
        updateRequests.add("finish");
        var end = updateEndStatus;
        answer =
            failure.equals("finish")
                ? updateError("UpdateEnd", 6, "Cannot finish")
                : end.isEmpty()
                    ? Resources.text(FakeDevice.class, "fake-device--update-end-empty.xml").strip()
                    : updateAnswerOf(
                        "UpdateEnd",
                        end,
                        Resources.text(FakeDevice.class, "fake-device--update-end-comments.txt")
                            .strip());
      } else {
        updateRequests.add("poll");
        if (updatePollAnswer != null) {
          answer = updatePollAnswer;
        } else {
          var steps = updateProgress;
          var working = pollIndex < steps.size();
          var percent = working ? steps.get(pollIndex) : 100;
          pollIndex++;
          if (failure.equals("progress") && pollIndex >= 2) {
            answer = updateError("UpdateProgress", 7, "Installer result code: 2");
          } else {
            answer =
                updateAnswerOf(
                    "UpdateProgress",
                    working ? "InProgress" : "Success",
                    Resources.text(
                            FakeDevice.class,
                            "fake-device--update-progress-details.xml",
                            Map.of("percent", String.valueOf(percent)))
                        .strip());
          }
        }
      }
      fireHook();
      respond(exchange, 200, answer, setCookie);
    }
  }

  /** Handles pairing requests. */
  final class PairingEndpoint {
    private static String pairingNotAuthorized() {
      return Resources.text(FakeDevice.class, "fake-device--pairing-not-authorized.xml").strip();
    }

    private static String pairingError(int number, String description) {
      return Resources.text(
              FakeDevice.class,
              "fake-device--pairing-error.xml",
              Map.of("number", String.valueOf(number), "description", description))
          .strip();
    }

    /**
     * Handles a POST to pairing.cgi. An insert is answered with the authority of the device as the
     * text of {@code Pairing}, without {@code Status}; any other request with error 7; a request
     * without a session with error 6.
     */
    void handle(HttpExchange exchange) throws IOException {
      cgiRequests.incrementAndGet();
      var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      var cookie = exchange.getRequestHeaders().getFirst("Cookie");
      pairingBodies.add(body);
      cookiesSeen.add(cookie == null ? "" : cookie);
      if (dropNext > 0) {
        dropNext--;
        exchange.close();
        return;
      }
      var authorised = !alwaysRejectCookie && cookie != null && cookie.contains("dtmauth=" + token);
      String answer;
      if (!authorised) {
        answer = pairingNotAuthorized();
      } else {
        answer = pairingAnswer != null ? pairingAnswer : pairingAnswerFor(body);
      }
      fireHook();
      respond(exchange, 200, answer, null);
    }

    private String pairingAnswerFor(String body) {
      var form = new LinkedHashMap<String, String>();
      for (var pair : body.split("&")) {
        var parts = pair.split("=", 2);
        form.put(
            URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
            parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
      }
      var certdata = form.get("certdata");
      var deviceId = form.get("deviceid");
      if (authority == null || certdata == null || deviceId == null) {
        return pairingError(7, "Error - insert certificate failed.");
      }
      try {
        CertificateDetails.firstCertificate(certdata);
        pairings.put(deviceId, certdata);
        return Resources.text(
                FakeDevice.class,
                "fake-device--pairing-accepted.xml",
                Map.of("authority", authority.rootPem()))
            .strip();
      } catch (CertificateException _) {
        return pairingError(7, "Error - insert certificate failed.");
      } catch (GeneralSecurityException failure) {
        throw new IllegalStateException(failure);
      }
    }
  }

  /** Handles authentication requests. */
  final class AuthenticationEndpoint {
    private static String authenticationError(int number, String description) {
      return Resources.text(
              FakeDevice.class,
              "fake-device--authentication-error.xml",
              Map.of("number", String.valueOf(number), "description", description))
          .strip();
    }

    /**
     * Handles a certificate request posted to login.cgi: the certificate as PEM in {@code
     * application/octet-stream}, or error 260 in {@code CgiAuth} for a body of another content
     * type. The other errors are simulated.
     */
    void handle(HttpExchange exchange) throws IOException {
      cgiRequests.incrementAndGet();
      var body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      var cookie = exchange.getRequestHeaders().getFirst("Cookie");
      var query = exchange.getRequestURI().getRawQuery();
      var contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      authenticationRequests.add(new AuthenticationRequest(query, contentType, body));
      cookiesSeen.add(cookie == null ? "" : cookie);
      if (dropNext > 0) {
        dropNext--;
        exchange.close();
        return;
      }
      var authorised = !alwaysRejectCookie && cookie != null && cookie.contains("dtmauth=" + token);
      var answer = authorised ? authenticationAnswerFor(query, contentType, body) : "";
      fireHook();
      respond(
          exchange,
          200,
          answer,
          null,
          answer.startsWith("-----BEGIN CERTIFICATE-----")
              ? "application/octet-stream"
              : "text/xml");
    }

    private String authenticationAnswerFor(String query, String contentType, String body) {
      if (authenticationAnswer != null) {
        return authenticationAnswer;
      }
      if (!"request_version=3&cgiauth=1".equals(query)) {
        return authenticationError(2, "Error - invalid request, unknown parameter.");
      }
      if (!"application/octet-stream".equals(contentType)) {
        return authenticationError(260, "Expected ContentType of application/octet-stream");
      }
      if (authority == null || !body.startsWith("-----BEGIN")) {
        return authenticationError(1, "Error - invalid request, no parameter.");
      }
      try {
        var toSign =
            authenticationForAnotherKey
                ? CertificateRequest.withSubject(
                        RsaKeys.generate(1024),
                        List.of(CertificateRequest.Rdn.commonName("BBCGIAUTH other")),
                        SignatureScheme.SHA1_WITH_RSA)
                    .pem()
                : body;
        var issued = authority.sign(toSign);
        return issued.isEmpty()
            ? authenticationError(4, "Error - get certificate failed.")
            : issued.get().pem();
      } catch (GeneralSecurityException failure) {
        throw new IllegalStateException(failure);
      }
    }
  }
}
