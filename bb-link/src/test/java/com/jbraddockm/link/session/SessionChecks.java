package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.actions.ConfirmedByPin;
import com.jbraddockm.link.cgi.BodyFormat;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.XmlConversation;
import com.jbraddockm.link.pairing.PairDevice;
import com.jbraddockm.link.pairing.PairingAnswer;
import com.jbraddockm.link.pimsync.FindPimAccount;
import com.jbraddockm.link.pimsync.GetPimItems;
import com.jbraddockm.link.pimsync.ListPimAccounts;
import com.jbraddockm.link.pimsync.PimAnswer;
import com.jbraddockm.link.pimsync.PimCommand;
import com.jbraddockm.link.pimsync.PimFilters;
import com.jbraddockm.link.pimsync.PimService;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.trace.TraceRecorder;
import com.jbraddockm.link.xml.MalformedXmlException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Checks the login, the retry rules and the failure mapping against a fake device. */
public final class SessionChecks {

  private static final String GOOD_CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\nQUFB\n-----END CERTIFICATE-----\n";

  private SessionChecks() {}

  private static DeviceSession session(FakeDevice device, String password) {
    return session(device, password, ExchangeObserver.NONE);
  }

  private static DeviceSession session(
      FakeDevice device, String password, ExchangeObserver observer) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            TransportScheme.HTTP,
            // an empty password stands for a device without one, in these tests only
            password.isEmpty()
                ? new Authentication.NoPassword()
                : new Authentication.Password(password)),
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
    pimSync();
    pairing();
  }

  private static Optional<ResponseEnvelope> envelopeOf(String body) {
    try {
      return Optional.of(ResponseEnvelope.parse(body));
    } catch (MalformedXmlException _) {
      return Optional.empty();
    }
  }

  private static CommandOutcome.Succeeded<PimAnswer> answered(CommandOutcome<PimAnswer> outcome) {
    if (outcome instanceof CommandOutcome.Succeeded<PimAnswer> succeeded) {
      return succeeded;
    }
    check(false, "the command should succeed: " + outcome);
    throw new IllegalStateException("unexpected outcome " + outcome);
  }

  private static boolean rejects(Runnable creation) {
    try {
      creation.run();
      return false;
    } catch (InvalidArgumentException _) {
      return true;
    }
  }

  /** The personal information sync: typed reading commands against the fake device. */
  private static void pimSync() throws Exception {
    var request = CgiRequest.Get.to(Endpoint.PIM_SYNC).expectingNoBody();
    var unauthenticated =
        new CgiResponse(200, "application/json", FakeDevice.UNAUTHENTICATED_PIM_SYNC, null);
    check(
        StaleSession.indicatedBy(request, unauthenticated, envelopeOf(unauthenticated.body())),
        "the real answer to a missing session is recognised");
    var empty = new CgiResponse(200, null, "", null);
    check(
        !StaleSession.indicatedBy(request, empty, Optional.empty()),
        "an empty answer is an answer, as pimsync gives to a request without a command");

    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      var logs = new Logs();
      var listed = answered(session.execute(new ListPimAccounts(), logs)).result();
      equal(PimCommand.LIST_ACCOUNTS, listed.command(), "list: command");
      check(
          listed.contentType().startsWith("application/json"),
          "list: the content type says the body is JSON");
      equal(
          listed.contentType(), listed.toJson().get("contentType"), "list: and it is on the wire");
      check(listed.service() == null, "list: no service");
      equal(0, listed.code(), "list: code");
      equal("OK", listed.message(), "list: message");
      equal(200, listed.httpStatus(), "list: HTTP status");
      equal(BodyFormat.JSON, listed.body().format(), "list: the answer is JSON");
      equal(
          Json.parse(FakeDevice.DEFAULT_PIM_ANSWER),
          listed.body().value(),
          "list: the body is the JSON itself, not a string");
      check(
          logs.mentions("Listing the PIM accounts") && !logs.mentions(".cgi"),
          "list: announced by what it does");

      answered(session.execute(new FindPimAccount("WS 1&x"), new Logs()));
      answered(
          session.execute(
              new GetPimItems("WS1", PimService.CONTACTS, PimFilters.none()), new Logs()));
      var everything =
          new PimFilters(
              Optional.of("2026-10-15T09:00:00Z"),
              OptionalInt.of(50),
              true,
              true,
              OptionalInt.of(100));
      answered(session.execute(new GetPimItems("WS1", PimService.EVENTS, everything), new Logs()));
      equal(
          List.of(
              "GET /cgi-bin/pimsync.cgi?command=list_accounts",
              "GET /cgi-bin/pimsync.cgi?command=find_accountId&workstation_id=WS%201%26x",
              "GET /cgi-bin/pimsync.cgi?command=get&workstation_id=WS1&service=contacts",
              "GET /cgi-bin/pimsync.cgi?command=get&workstation_id=WS1&service=events"
                  + "&updated_time=2026-10-15T09%3A00%3A00Z&limit=50&dirty_only=true"
                  + "&local_only=true&offset=100"),
          device.pimRequests(),
          "the requests, with their parameters in a fixed order and encoded");
      equal(2, device.loginRequests.get(), "one login for all of them");
    }

    // the answer is passed on untouched, whatever it looks like
    var invalidJson =
        "{\"version\":\"1.0\",\"status\":{\"code\":0,\"msg\":\"say \\\"hi\\\"\"},\"data\":oops &"
            + " <x>}";
    var json = Json.class;
    var cases =
        Map.of(
            invalidJson,
            new Object[] {0, "say \"hi\"", BodyFormat.TEXT, invalidJson},
            "[1,2,3]",
            new Object[] {null, null, BodyFormat.JSON, Json.parse("[1,2,3]")},
            "not json at all\r\n",
            new Object[] {null, null, BodyFormat.TEXT, "not json at all\r\n"},
            "{\"status\":{\"code\":7}}",
            new Object[] {7, null, BodyFormat.JSON, Json.parse("{\"status\":{\"code\":7}}")});
    for (var entry : cases.entrySet()) {
      try (var device = new FakeDevice("secret");
          var session = session(device, "secret")) {
        device.pimResponses.put("list_accounts", entry.getKey());
        var answer = answered(session.execute(new ListPimAccounts(), new Logs())).result();
        equal(
            entry.getValue()[2],
            answer.body().format(),
            "what the body was made into: " + entry.getKey());
        equal(
            entry.getValue()[3],
            answer.body().value(),
            "the body: JSON if it parses, otherwise the text as sent");
        equal(entry.getValue()[0], answer.code(), "code read from: " + entry.getKey());
        equal(entry.getValue()[1], answer.message(), "message read from: " + entry.getKey());
        equal(entry.getValue()[3], answer.toJson().get("body"), "and so is the wire body");
        equal(
            ((BodyFormat) entry.getValue()[2]).wireName(),
            answer.toJson().get("bodyFormat"),
            "with its format on the wire");
      }
    }

    // the device rejects a request
    for (var code : List.of(1001, 1013, 1015)) {
      try (var device = new FakeDevice("secret");
          var session = session(device, "secret")) {
        device.pimResponses.put(
            "get",
            "{\"version\":\"1.0\",\"status\":{\"code\":"
                + code
                + ",\"msg\":\"no thanks\"},\"data\":null}");
        var outcome =
            failed(
                session.execute(
                    new GetPimItems("WS1", PimService.CALENDARS, PimFilters.none()), new Logs()));
        equal("COMMAND_FAILED", code(outcome), "device status " + code);
        equal("no thanks", outcome.error().message(), "the device's message " + code);
        equal(code, outcome.error().details().get("code"), "and its code " + code);
        check(
            outcome.partialResult().isPresent()
                && String.valueOf(((PimAnswer) outcome.partialResult().get()).body().value())
                    .contains("no thanks"),
            "the answer is kept, so the body can still be read: " + code);
      }
    }

    // a lost session: renewed once, and an answer that stays "not logged in" is a failure
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      session.execute(new ListPimAccounts(), new Logs());
      device.expireSessions();
      var logs = new Logs();
      equal(
          "SUCCEEDED",
          code(session.execute(new ListPimAccounts(), logs)),
          "a lost session is renewed");
      check(logs.mentions("forgot the session"), "and the renewal is logged");
      equal(4, device.loginRequests.get(), "with one new login");
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      device.pimResponses.put("list_accounts", FakeDevice.UNAUTHENTICATED_PIM_SYNC);
      var outcome = failed(session.execute(new ListPimAccounts(), new Logs()));
      equal("AUTH_FAILED", code(outcome), "an answer that stays \"not logged in\" fails");
      equal(4, device.loginRequests.get(), "after one renewal, not in a loop");
    }

    // these commands only read, so one dropped connection is tried again
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      session.execute(new ListPimAccounts(), new Logs());
      var before = device.cgiRequests.get();
      device.dropNext = 1;
      var logs = new Logs();
      equal("SUCCEEDED", code(session.execute(new ListPimAccounts(), logs)), "a read is repeated");
      equal(before + 2, device.cgiRequests.get(), "once");
      check(logs.mentions("connecting again"), "and the retry is logged");
    }

    // arguments are checked before anything is sent
    check(rejects(() -> new FindPimAccount(" ")), "a blank workstation");
    check(
        rejects(() -> new GetPimItems("", PimService.CONTACTS, PimFilters.none())),
        "no workstation");
    check(rejects(() -> PimService.of("photos")), "photos cannot be read as text");
    check(rejects(() -> PimService.of("Contacts")), "service names are exact");
    check(
        rejects(
            () ->
                new PimFilters(
                    Optional.empty(), OptionalInt.empty(), false, false, OptionalInt.of(5))),
        "an offset needs localOnly");
    check(
        rejects(
            () ->
                new PimFilters(
                    Optional.empty(), OptionalInt.of(0), false, false, OptionalInt.empty())),
        "limit 0");
    check(
        rejects(
            () ->
                new PimFilters(
                    Optional.empty(), OptionalInt.empty(), false, true, OptionalInt.of(-1))),
        "negative offset");
    check(
        rejects(
            () ->
                new PimFilters(
                    Optional.of(" "), OptionalInt.empty(), false, false, OptionalInt.empty())),
        "blank time");

    var directory = Files.createTempDirectory("bb-link-pim-trace");
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret", new TraceRecorder(directory))) {
      session.execute(new ListPimAccounts(), new Logs());
      try (var files = Files.list(directory)) {
        var traces = files.sorted().map(path -> read(path)).collect(Collectors.joining("\n"));
        check(
            traces.contains(">>> GET /cgi-bin/pimsync.cgi?command=list_accounts"),
            "the trace shows the request with its query");
        check(
            Pattern.compile(">>> Cookie: [^\\n]*dtmauth=\\*\\*\\*").matcher(traces).find(),
            "with its cookie by name");
      }
    }
  }

  private static CommandOutcome<PairingAnswer> pair(
      DeviceSession session, String certificate, String confirmPin, Logs logs) {
    return session.execute(
        new ConfirmedByPin<>(new PairDevice("PEER-1", certificate, Optional.empty()), confirmPin),
        logs);
  }

  /**
   * Enrolling a certificate: the guard, what is sent, what comes back and what is never repeated.
   */
  private static void pairing() throws Exception {
    // a wrong PIN sends nothing that could change the device, and does not reveal the real PIN
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      var outcome = failed(pair(session, "not a certificate", "00000000", new Logs()));
      equal("PIN_MISMATCH", code(outcome), "a wrong confirmation PIN");
      check(!outcome.error().message().contains("2A3B4C5D"), "the error does not reveal the PIN");
      equal(List.of(), device.pairingBodies(), "nothing was sent to pairing");
      equal(1, device.cgiRequests.get(), "only the PIN was read");
    }

    // the PIN may be written in either case and with 0x; a certificate that is not one is forwarded
    // as is
    for (var confirmation : List.of("2A3B4C5D", "2a3b4c5d", "0x2A3B4C5D", " 2A3B4C5D ")) {
      try (var device = new FakeDevice("secret");
          var session = session(device, "secret")) {
        var logs = new Logs();
        var outcome = failed(pair(session, "not a certificate", confirmation, logs));
        equal("COMMAND_FAILED", code(outcome), "the device rejects the text: " + confirmation);
        equal(
            "Error - insert certificate failed.", outcome.error().message(), "with its own words");
        equal(7, outcome.error().details().get("errorIdentifier"), "and its error number");
        equal(
            List.of("certdata=not%20a%20certificate&deviceid=PEER-1"),
            device.pairingBodies(),
            "the text reached the device unchanged");
        check(
            logs.mentions("Enrolling a client certificate")
                && !logs.mentions("not a certificate")
                && !logs.mentions(".cgi"),
            "the log says what is done and never shows the certificate");
        if (outcome.partialResult().orElseThrow() instanceof PairingAnswer answer) {
          equal("Error", answer.status(), "the answer is kept: status");
          check(
              answer.contentType().startsWith("text/xml"),
              "the answer is kept: the content type says the body is XML");
          equal(answer.contentType(), answer.toJson().get("contentType"), "and it is on the wire");
          check(
              answer.body().format() == BodyFormat.XML
                  && String.valueOf(answer.body().value())
                      .contains("Error - insert certificate failed."),
              "the answer is kept: body");
        }
      }
    }

    // a certificate, with the optional account identifier
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      var outcome =
          session.execute(
              new ConfirmedByPin<>(
                  new PairDevice("PEER-1", GOOD_CERTIFICATE, Optional.of("EC-1")), "2A3B4C5D"),
              new Logs());
      if (outcome instanceof CommandOutcome.Succeeded<PairingAnswer>(var answer, _)) {
        equal("Success", answer.status(), "a certificate that parses");
      } else {
        check(false, "a certificate that parses should succeed: " + outcome);
      }
      equal(
          List.of(
              "certdata=-----BEGIN%20CERTIFICATE-----%0AQUFB%0A-----END%20CERTIFICATE-----%0A"
                  + "&deviceid=PEER-1&ecoid=EC-1"),
          device.pairingBodies(),
          "the certificate is encoded with its line breaks, then the identifier, then the account");
    }

    // answers that are not understood are failures that still carry the body
    var odd =
        Map.of(
            "<RimTabletResponse><Pairing><Status>Odd</Status></Pairing></RimTabletResponse>",
                "UNEXPECTED_RESPONSE",
            "<RimTabletResponse><Other/></RimTabletResponse>", "UNEXPECTED_RESPONSE",
            "ok", "UNEXPECTED_RESPONSE");
    for (var entry : odd.entrySet()) {
      try (var device = new FakeDevice("secret");
          var session = session(device, "secret")) {
        device.pairingAnswer = entry.getKey();
        var outcome = failed(pair(session, GOOD_CERTIFICATE, "2A3B4C5D", new Logs()));
        equal(
            entry.getValue(), code(outcome), "an answer that is not understood: " + entry.getKey());
        check(
            outcome.partialResult().isPresent()
                && ((PairingAnswer) outcome.partialResult().get()).body().format()
                    == (entry.getKey().equals("ok") ? BodyFormat.TEXT : BodyFormat.XML),
            "and its body is kept: " + entry.getKey());
      }
    }

    // a session the device has forgotten before the first request is renewed
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      device.expireSessions();
      var logs = new Logs();
      var outcome = pair(session, GOOD_CERTIFICATE, "2A3B4C5D", logs);
      equal("SUCCEEDED", code(outcome), "a forgotten session is renewed before anything is sent");
      check(logs.mentions("forgot the session"), "and the renewal is logged");
      equal(1, device.pairingBodies().size(), "the request was sent once");
    }

    // a session lost between reading the PIN and enrolling: no second try
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      device.afterNextCgiRequest = device::expireSessions;
      var outcome = failed(pair(session, GOOD_CERTIFICATE, "2A3B4C5D", new Logs()));
      equal("AUTH_FAILED", code(outcome), "a session lost in the middle of the command");
      check(outcome.error().message().contains("while the command was running"), "its message");
      equal(1, device.pairingBodies().size(), "the pairing request was not repeated");
    }

    // a dropped connection on the pairing request: no second try
    try (var device = new FakeDevice("secret");
        var session = session(device, "secret")) {
      properties(session, new Logs());
      var before = device.cgiRequests.get();
      device.afterNextCgiRequest = () -> device.dropNext = 1;
      var outcome = failed(pair(session, GOOD_CERTIFICATE, "2A3B4C5D", new Logs()));
      equal("CONNECTION_LOST", code(outcome), "a dropped connection");
      equal(before + 2, device.cgiRequests.get(), "the PIN and one pairing request, nothing more");
      equal(1, device.pairingBodies().size(), "the pairing request was not repeated");
    }

    // the real answer of a device to pairing without a session is recognised
    var unauthenticated =
        new CgiResponse(200, "text/xml", FakeDevice.UNAUTHENTICATED_PAIRING, null);
    check(
        StaleSession.indicatedBy(
            CgiRequest.Form.to(Endpoint.PAIRING),
            unauthenticated,
            envelopeOf(unauthenticated.body())),
        "the real answer to a missing session is recognised");

    // arguments are checked before anything is sent
    for (var identifier : Arrays.asList("", " ", "a/b", "a b", "a".repeat(65), "é", null)) {
      check(
          rejects(() -> new PairDevice(identifier, "x", Optional.empty())),
          "a device identifier that is not accepted: " + identifier);
    }
    check(
        new PairDevice("A".repeat(64), "x", Optional.empty()).deviceId().length() == 64,
        "64 characters are fine");
    check(rejects(() -> new PairDevice("A", " ", Optional.empty())), "a blank certificate");
    check(
        rejects(() -> new PairDevice("A", "x".repeat(16 * 1024 + 1), Optional.empty())),
        "a certificate over 16 KiB");
    check(rejects(() -> new PairDevice("A", "x", Optional.of(" "))), "a blank account identifier");
    check(
        rejects(() -> new ConfirmedByPin<>(new PairDevice("A", "x", Optional.empty()), " ")),
        "a blank confirmation PIN");
    check(
        !new PairDevice("A", "x".repeat(1024), Optional.empty()).exceedsDeviceLimit(),
        "1024 bytes are within the device's limit");
    check(
        new PairDevice("A", "x".repeat(1025), Optional.empty()).exceedsDeviceLimit(),
        "1025 bytes are over it");
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
        StaleSession.indicatedBy(request, empty, Optional.empty()),
        "an empty 200 answer is a lost session");
    check(
        !StaleSession.indicatedBy(request.expectingNoBody(), empty, Optional.empty()),
        "unless the request is answered with nothing when it succeeds");
    check(
        !StaleSession.indicatedBy(
            request, new CgiResponse(200, "text/xml", "<a/>", null), Optional.empty()),
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
          Map.of("failedAttempts", 1, "retriesRemaining", 4, "maxAttempts", 5),
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

    // the real shape: the description and the number sit in an Error element
    try (var device = new FakeDevice("secret")) {
      device.propertiesXml =
          "<RimTabletResponse><DynamicProperties><Status>Error</Status><Error>"
              + "<ErrorIdentifier>30</ErrorIdentifier>"
              + "<ErrorDescription>Error - something went wrong.</ErrorDescription>"
              + "</Error></DynamicProperties></RimTabletResponse>";
      try (var session = session(device, "secret")) {
        var outcome = failed(properties(session, new Logs()));
        equal("COMMAND_FAILED", code(outcome), "a nested device-reported error");
        equal(
            "Error - something went wrong.",
            outcome.error().message(),
            "carries the device's words");
        equal(30, outcome.error().details().get("errorIdentifier"), "and its error number");
        equal("DynamicProperties", outcome.error().details().get("element"), "and the element");
      }
    }
    try (var device = new FakeDevice("secret")) {
      device.propertiesXml =
          "<RimTabletResponse><DynamicProperties><Status>Error</Status></DynamicProperties></RimTabletResponse>";
      try (var session = session(device, "secret")) {
        var outcome = failed(properties(session, new Logs()));
        equal(
            "The device reported an error",
            outcome.error().message(),
            "no description: generic text");
        check(
            !outcome.error().details().containsKey("errorIdentifier"), "and no number is invented");
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

  private static String read(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

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
      return new XmlConversation<>() {
        private int answered;

        @Override
        public Step<NoData> start() {
          return new Step.Send<>(
              CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES)
                  .echo(FormField.GET_DYNAMIC_PROPERTIES));
        }

        @Override
        protected Step<NoData> onResponse(ResponseEnvelope response) {
          return answered++ == 0
              ? new Step.Send<>(
                  CgiRequest.Form.to(Endpoint.WIPE).withText(FormField.WIPE, "getpin"))
              : new Step.Finish<>(NoData.INSTANCE);
        }
      };
    }
  }
}
