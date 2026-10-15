package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.trace.TraceRecorder;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** Checks the login, the retry rules and the failure mapping against a fake device. */
public final class SessionChecks {

  private SessionChecks() {}

  /** Collects the log messages of a command. */
  private static final class Logs implements CommandListener {
    final List<String> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onLog(LogLevel level, String message) {
      messages.add(message);
    }

    @Override
    public void onProgress(Progress progress) {}

    boolean mentions(String text) {
      return messages.stream().anyMatch(message -> message.contains(text));
    }
  }

  /** A command with two requests, to check what happens when the session ends in between. */
  private record TwoStep(boolean readOnly) implements DeviceCommand<NoData> {
    @Override
    public Conversation<NoData> conversation() {
      return new Conversation<>() {
        private int answered;

        @Override
        public Step<NoData> start() {
          return new Step.Send<>(
              CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES)
                  .echo(FormField.GET_DYNAMIC_PROPERTIES));
        }

        @Override
        public Step<NoData> onResponse(ResponseEnvelope response) {
          return answered++ == 0
              ? new Step.Send<>(
                  CgiRequest.Form.to(Endpoint.WIPE).withText(FormField.WIPE, "getpin"))
              : new Step.Finish<>(NoData.INSTANCE);
        }
      };
    }
  }

  private static DeviceSession session(FakeDevice device, String password) {
    return session(device, password, ExchangeObserver.NONE);
  }

  private static DeviceSession session(
      FakeDevice device, String password, ExchangeObserver observer) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            password,
            TransportScheme.HTTP),
        observer);
  }

  private static CommandOutcome<DeviceProperties> properties(DeviceSession session, Logs logs) {
    return session.execute(new GetDeviceProperties(), logs);
  }

  private static String code(CommandOutcome<?> outcome) {
    return outcome instanceof CommandOutcome.Failed<?> failed
        ? failed.error().code().wireName()
        : "SUCCEEDED";
  }

  private static CommandOutcome.Failed<?> failed(CommandOutcome<?> outcome) {
    return (CommandOutcome.Failed<?>) outcome;
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    login();
    loginFailures();
    staleSessions();
    connectionFailures();
    scrubbing();
    realLoginAnswers();
    cgiCookies();
    emptyAnswers();
    shareCredentials();
  }

  /** The file share credentials come from the login and change with every login. */
  private static void shareCredentials() throws Exception {
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      check(session.smbCredentials().isEmpty(), "no credentials before the first login");
      properties(session, new Logs());
      var first = session.smbCredentials().orElseThrow();
      equal("dtm", first.user(), "the share user is fixed");
      equal(device.currentSharePassword(), first.password(), "the password of the login");
      equal("WORKGROUP", first.workgroup(), "workgroup");
      equal("%h", first.netBios(), "the NetBIOS name is passed on as the device writes it");
      check(!first.toString().contains(first.password()), "toString never shows the password");

      device.expireSessions();
      properties(session, new Logs());
      var renewed = session.smbCredentials().orElseThrow();
      check(!renewed.password().equals(first.password()), "a new login brings a new password");
      equal(
          device.currentSharePassword(), renewed.password(), "and the session reports the latest");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.offersShare = false;
      equal(
          "SUCCEEDED",
          code(properties(session, new Logs())),
          "a device may hand out no credentials");
      check(session.smbCredentials().isEmpty(), "then there are none");
    }
  }

  /**
   * An empty answer means "no session", unless the request is known to be answered with nothing.
   */
  private static void emptyAnswers() {
    var empty = new CgiResponse(200, null, "", null);
    var request = CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES);
    check(
        StaleSession.indicatedBy(request, empty, java.util.Optional.empty()),
        "an empty 200 answer is a lost session");
    check(
        !StaleSession.indicatedBy(request.expectingNoBody(), empty, java.util.Optional.empty()),
        "unless the request is answered with nothing when it succeeds");
    check(
        !StaleSession.indicatedBy(
            request, new CgiResponse(200, "text/xml", "<a/>", null), java.util.Optional.empty()),
        "an answer with content is not a lost session");
  }

  /** The three login answers as a real device sends them, with the share password made up. */
  private static void realLoginAnswers() throws Exception {
    var challenge =
        LoginDocument.of(
            new CgiResponse(
                200,
                "text/xml",
                """
                <RimTabletResponse>
                  <AuthChallenge>
                    <Status>PasswdChallenge</Status>
                    <Challenge>What's the airspeed velocity of an unladen swallow?3Pfa94fK6iYorCvomr32L2K5h2al</Challenge>
                    <Algorithm>2</Algorithm>
                    <Salt>4EB86E31B720EAFF</Salt>
                    <ICount>6000</ICount>
                    <FailedAttempts>0</FailedAttempts>
                    <RetriesRemaining>5</RetriesRemaining>
                  </AuthChallenge>
                </RimTabletResponse>
                """,
                "loginsession=x; expires=Wed, 14-Oct-2026 13:37:31 GMT; path=/"));
    equal(LoginStatus.PASSWORD_CHALLENGE, challenge.status(), "real challenge: status");
    equal(2, challenge.integer(LoginKey.ALGORITHM), "real challenge: algorithm");
    equal(6000, challenge.integer(LoginKey.ITERATION_COUNT), "real challenge: iterations");
    equal("4EB86E31B720EAFF", challenge.required(LoginKey.SALT), "real challenge: salt");
    check(
        challenge.required(LoginKey.CHALLENGE).startsWith("What's the airspeed"),
        "real challenge: text with spaces and an apostrophe survives");

    var success =
        LoginDocument.of(
            new CgiResponse(
                200,
                "text/xml",
                "<RimTabletResponse><Auth><Status>Success</Status><Smb><Credentials User='dtm'"
                    + " Password='MADE-UP'/><User>dtm</User><Pwd>MADE-UP</Pwd>"
                    + "<Workgroup>WORKGROUP</Workgroup><NetBios>%h</NetBios></Smb></Auth></RimTabletResponse>",
                null));
    equal(LoginStatus.SUCCESS, success.status(), "real success answer");
    var share = success.smb().orElseThrow();
    equal("dtm", share.user(), "real success answer: share user");
    equal("MADE-UP", share.password(), "real success answer: share password");
    equal("%h", share.netBios(), "real success answer: NetBIOS name");

    var denied =
        LoginDocument.of(
            new CgiResponse(
                200,
                "text/xml",
                "<RimTabletResponse><Auth><Status>Denied</Status><FailedAttempts>1</FailedAttempts>"
                    + "<RetriesRemaining>4</RetriesRemaining></Auth></RimTabletResponse>",
                null));
    equal(LoginStatus.DENIED, denied.status(), "real denial");
    equal(1, denied.denial().failedAttempts(), "denial: failed attempts");
    equal(4, denied.denial().remainingAttempts(), "denial: remaining attempts");
  }

  /** The device renews the cookie in every CGI answer; what the client does with it matters. */
  private static void cgiCookies() throws Exception {
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      properties(session, new Logs());
      equal(2, device.loginRequests.get(), "a renewed cookie keeps the session: one login only");
      check(
          device.cookiesSeen().getLast().contains("dtmauth="), "and is sent with the next command");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.cgiCookie = FakeDevice.CgiCookie.EXPIRED;
      var first = new Logs();
      equal("SUCCEEDED", code(properties(session, first)), "first command works");
      var second = new Logs();
      equal("SUCCEEDED", code(properties(session, second)), "so does the second");
      check(
          second.mentions("forgot the session"),
          "an expired cookie in a CGI answer is dropped, so the session is renewed every time");
      equal(4, device.loginRequests.get(), "at the price of one extra login per command");
    }
  }

  private static void login() throws Exception {
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      var logs = new Logs();
      var outcome = properties(session, logs);
      check(
          outcome instanceof CommandOutcome.Succeeded<DeviceProperties>,
          "login and command succeed");
      if (outcome instanceof CommandOutcome.Succeeded<DeviceProperties>(var result, _)) {
        equal("Test Z10", result.deviceName(), "device name parsed");
        equal("10.3.3.2205", result.operatingSystem().orElseThrow().version(), "os version");
      }
      equal(2, device.loginRequests.get(), "challenge login takes two requests");
      equal(
          List.of(FakeDevice.PROPERTIES_BODY),
          device.cgiBodies(),
          "the request body is percent-encoded");
      check(
          device.cookiesSeen().getFirst().contains("dtmauth="),
          "the cookie is sent with the command");
      check(
          logs.mentions("Reading the device properties") && !logs.mentions(".cgi"),
          "the request is announced by what it does, not by the program's name");
      properties(session, logs);
      equal(2, device.loginRequests.get(), "the session is reused, no second login");
    }

    try (var device = new FakeDevice("");
        var session = session(device, "")) {
      device.loginMode = FakeDevice.LoginMode.NO_PASSWORD;
      equal("SUCCEEDED", code(properties(session, new Logs())), "device without a password");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.cookieStyle = FakeDevice.CookieStyle.EXPIRED;
      equal(
          "SUCCEEDED",
          code(properties(session, new Logs())),
          "cookie with a past expiry is rescued");
      equal("SUCCEEDED", code(properties(session, new Logs())), "and keeps working");
      check(device.cookiesSeen().getLast().contains("dtmauth="), "the rescued cookie is sent");
    }
  }

  private static void loginFailures() throws Exception {
    try (var device = new FakeDevice("secret");
        var session = session(device, "wrong")) {
      var outcome = failed(properties(session, new Logs()));
      equal("AUTH_FAILED", code(outcome), "wrong password");
      equal(
          java.util.Map.of("failedAttempts", 1, "retriesRemaining", 4, "maxAttempts", 5),
          outcome.error().details(),
          "attempt counts");
      equal(
          "Authentication failed. There have been 1 of 5 failed attempts",
          outcome.error().message(),
          "the wording of the original tool");
      equal(0, device.cgiRequests.get(), "no command is sent after a failed login");

      device.failedAttempts = 4;
      var locked = failed(properties(session, new Logs()));
      equal("AUTH_LOCKED", code(locked), "last attempt used");
      equal(5, locked.error().details().get("failedAttempts"), "locked: attempts used");
      check(locked.error().message().contains("too many failed attempts (5)"), "locked wording");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.cookieStyle = FakeDevice.CookieStyle.MISSING;
      var outcome = failed(properties(session, new Logs()));
      equal("AUTH_FAILED", code(outcome), "no cookie issued");
      equal(
          "Authentication failed due to invalid password",
          outcome.error().message(),
          "its message");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.loginMode = FakeDevice.LoginMode.GARBAGE;
      var outcome = failed(properties(session, new Logs()));
      equal("DEVICE_ERROR", code(outcome), "login answer is not XML");
      check(
          String.valueOf(outcome.error().details().get("extra"))
              .startsWith("No content or invalid xml"),
          "the reason is kept");
      device.loginMode = FakeDevice.LoginMode.LEGACY;
      var legacy = failed(properties(session, new Logs()));
      equal("DEVICE_ERROR", code(legacy), "legacy login is refused clearly");
      check(
          String.valueOf(legacy.error().details().get("extra")).contains("legacy"), "and says why");
    }
  }

  private static void staleSessions() throws Exception {
    for (var style : FakeDevice.StaleStyle.values()) {
      try (var device = new FakeDevice("secret");
          var session = session(device, "secret")) {
        device.staleStyle = style;
        properties(session, new Logs());
        device.expireSessions();
        var logs = new Logs();
        equal(
            "SUCCEEDED", code(properties(session, logs)), "forgotten session is renewed: " + style);
        equal(4, device.loginRequests.get(), "one new login: " + style);
        check(logs.mentions("forgot the session"), "the renewal is logged: " + style);
      }
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.alwaysRejectCookie = true;
      var outcome = failed(properties(session, new Logs()));
      equal("AUTH_FAILED", code(outcome), "a device that never accepts the cookie");
      check(outcome.error().message().contains("right after a new login"), "its message");
      equal(4, device.loginRequests.get(), "renewed once, not in a loop");
      equal(2, device.cgiRequests.get(), "two attempts at most");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.afterNextCgiRequest = device::expireSessions;
      var outcome = failed(session.execute(new TwoStep(false), new Logs()));
      equal("AUTH_FAILED", code(outcome), "session lost between the two requests");
      check(outcome.error().message().contains("while the command was running"), "its message");
      equal(2, device.cgiRequests.get(), "the second request was not repeated");
      equal(2, device.loginRequests.get(), "no renewal in the middle of a command");
      equal("SUCCEEDED", code(properties(session, new Logs())), "the next command logs in again");
      equal(4, device.loginRequests.get(), "with one new login");
    }
  }

  private static void connectionFailures() throws Exception {
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      device.dropNext = 1;
      var logs = new Logs();
      equal(
          "SUCCEEDED",
          code(properties(session, logs)),
          "a read is repeated after a dropped connection");
      equal(3, device.cgiRequests.get(), "HttpClient did not retry on its own");
      check(logs.mentions("connecting again"), "the retry is logged");
    }

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      device.dropNext = 1;
      var outcome = failed(session.execute(new TwoStep(false), new Logs()));
      equal("CONNECTION_LOST", code(outcome), "a command that changes the device is not repeated");
      equal("reset", outcome.error().details().get("cause"), "cause");
      equal(2, device.cgiRequests.get(), "exactly one attempt, no hidden retry");
      equal(
          "SUCCEEDED",
          code(properties(session, new Logs())),
          "the session recovers on the next command");
    }

    FakeDevice closed;
    try (var device = new FakeDevice("secret")) {
      closed = device;
    }
    try (var session = session(closed, "secret")) {
      var outcome = failed(properties(session, new Logs()));
      equal("CONNECTION_FAILED", code(outcome), "nothing is listening");
      equal("refused", outcome.error().details().get("cause"), "cause");
    }

    try (var device = new FakeDevice("secret")) {
      var session = session(device, "secret");
      session.close();
      equal("INTERNAL", code(properties(session, new Logs())), "a closed session is an error");
    }
  }

  private static void scrubbing() throws IOException, Exception {
    try (var device = new FakeDevice("secret")) {
      device.propertiesXml =
          "<RimTabletResponse><DynamicProperties><Status>Error</Status>"
              + "<ErrorDescription>boom HTTP_COOKIE=dtmauth=LEAKED; a=b</ErrorDescription>"
              + "</DynamicProperties></RimTabletResponse>";
      try (var session = session(device, "secret")) {
        var outcome = failed(properties(session, new Logs()));
        equal("COMMAND_FAILED", code(outcome), "device-reported error");
        equal("boom", outcome.error().message(), "cookie echo removed from the message");
        check(
            outcome.raw().stream().noneMatch(text -> text.contains("LEAKED")),
            "raw output is scrubbed");
      }
    }

    var directory = Files.createTempDirectory("bb-link-trace");
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret", new TraceRecorder(directory))) {
      device.propertiesXml =
          "<RimTabletResponse><DynamicProperties><BoardSerialNumber>REALSERIAL</BoardSerialNumber>"
              + "<Bbid>REALBBID</Bbid><Imei>359999999999999</Imei><Meid></Meid>"
              + "<DeviceName>Mine</DeviceName></DynamicProperties></RimTabletResponse>";
      properties(session, new Logs());
      try (var files = Files.list(directory)) {
        var traces =
            files.sorted().map(path -> read(path)).collect(Collectors.joining("\n=====\n"));
        equal(3, traces.split("=====", -1).length, "login, answer and command are traced");
        check(
            !traces.contains("REALSERIAL") && !traces.contains("REALBBID"),
            "identifiers are masked");
        check(
            traces.contains("<BoardSerialNumber>***</BoardSerialNumber>"), "the masks are visible");
        check(traces.contains("Mine"), "other values are kept");
        check(!traces.contains("TOKEN-1"), "the cookie value is masked");
        check(!traces.contains("PRELOGIN-TOKEN"), "the cookie issued before the login is masked");
        check(
            !traces.contains(FakeDevice.SHARE_PASSWORD_PREFIX),
            "the file share password is masked");
        check(traces.contains("Password='***'"), "its mask is visible");
        check(!traces.contains("359999999999999"), "the IMEI is masked");
        check(traces.contains("<Meid></Meid>"), "an empty value stays empty");
        check(
            traces.contains(">>> Cookie: dtmauth=***"), "the trace shows that the cookie was sent");
        check(traces.contains("dtmauth=***"), "its mask is visible");
        check(traces.contains("challenge_data=***"), "the password hash is masked");
      }
    }
  }

  private static String read(java.nio.file.Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new java.io.UncheckedIOException(failure);
    }
  }
}
