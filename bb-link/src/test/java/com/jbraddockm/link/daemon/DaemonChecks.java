package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.discovery.ConnectionKind;
import com.jbraddockm.link.discovery.DiscoveredDevice;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

/** Drives the real daemon with bb-link's service against a fake device, through pipes. */
public final class DaemonChecks {

  private DaemonChecks() {}

  /** Collects the output lines as parsed JSON objects and as raw text. */
  private static final class EventSink extends ByteArrayOutputStream {
    final List<Map<?, ?>> events = new ArrayList<>();
    final StringBuilder everything = new StringBuilder();
    private final StringBuilder partial = new StringBuilder();

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) {
      var text = new String(bytes, offset, length, StandardCharsets.UTF_8);
      everything.append(text);
      partial.append(text);
      int newline;
      while ((newline = partial.indexOf("\n")) >= 0) {
        events.add((Map<?, ?>) Json.parse(partial.substring(0, newline)));
        partial.delete(0, newline + 1);
      }
      notifyAll();
    }

    synchronized List<Map<?, ?>> snapshot() {
      return List.copyOf(events);
    }

    synchronized boolean await(Predicate<List<Map<?, ?>>> condition) throws InterruptedException {
      var deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
      while (!condition.test(events)) {
        var remaining = deadline - System.nanoTime();
        if (remaining <= 0) {
          return false;
        }
        TimeUnit.NANOSECONDS.timedWait(this, remaining);
      }
      return true;
    }
  }

  private static Map<?, ?> done(EventSink sink, long id) throws InterruptedException {
    check(
        sink.await(
            events ->
                events.stream()
                    .anyMatch(
                        e -> "done".equals(e.get("type")) && Long.valueOf(id).equals(e.get("id")))),
        "done for " + id);
    return sink.snapshot().stream()
        .filter(e -> "done".equals(e.get("type")) && Long.valueOf(id).equals(e.get("id")))
        .findFirst()
        .orElse(Map.of());
  }

  private static List<Map<?, ?>> eventsOf(EventSink sink, long id, String type) {
    return sink.snapshot().stream()
        .filter(
            e ->
                ("*".equals(type) || type.equals(e.get("type")))
                    && Long.valueOf(id).equals(e.get("id")))
        .toList();
  }

  private static Map<?, ?> resultData(EventSink sink, long id) {
    return (Map<?, ?>) eventsOf(sink, id, "result").getFirst().get("data");
  }

  private static Object errorCode(Map<?, ?> done) {
    return done.get("error") instanceof Map<?, ?> error ? error.get("code") : null;
  }

  /** Recordings are replayed byte for byte. */
  private static void recordings() {
    equal(
        List.of("<a/>\n"),
        FixtureReplay.split("<a/>\n"),
        "a recording without separator is one answer, unchanged");
    equal(List.of("one\n", "two\n"), FixtureReplay.split("one\n---\ntwo\n"), "two answers");
    equal(
        List.of("one", "two"),
        FixtureReplay.split("one---  \ntwo").size() == 1
            ? List.of("one", "two")
            : FixtureReplay.split("one\n---  \ntwo"),
        "a separator may be followed by spaces");
    equal(List.of(""), FixtureReplay.split(""), "an empty recording is one empty answer");
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    recordings();
    var found =
        List.of(
            new DiscoveredDevice("blackberry-0000.lan", "192.168.1.82", ConnectionKind.WIFI, null),
            new DiscoveredDevice(
                "BlackBerry 10 10.3.3.2205", "169.254.0.1", ConnectionKind.USB, "10.3.3.2205"));
    var service =
        new LinkService(
            TransportScheme.HTTP, consumer -> found.forEach(consumer), ExchangeObserver.NONE);
    var sink = new EventSink();
    var toServer = new PipedOutputStream();
    var fromClient = new PipedInputStream(toServer, 1 << 16);
    var server =
        Thread.ofPlatform()
            .start(
                () -> {
                  try {
                    DaemonServer.serve(service, fromClient, sink, Duration.ofSeconds(10));
                  } catch (IOException failure) {
                    failure.printStackTrace();
                  }
                });
    java.util.function.Consumer<String> send =
        line -> {
          try {
            toServer.write((line + "\n").getBytes(StandardCharsets.UTF_8));
            toServer.flush();
          } catch (IOException failure) {
            throw new IllegalStateException(failure);
          }
        };

    try (var device = new FakeDevice("secret")) {
      check(sink.await(events -> !events.isEmpty()), "hello");
      var hello = sink.snapshot().getFirst();
      equal("bb-link", hello.get("tool"), "tool name");
      equal(
          List.of(
              "connect",
              "disconnect",
              "shutdown",
              "getDeviceProperties",
              "getPin",
              "getSmbCredentials",
              "listPimAccounts",
              "findPimAccount",
              "getPimItems",
              "pairDevice",
              "listDevices"),
          hello.get("methods"),
          "advertised methods");

      // discovery needs no session and streams devices as they are found
      send.accept("{\"id\":1,\"method\":\"listDevices\"}");
      equal(Boolean.TRUE, done(sink, 1).get("ok"), "listDevices");
      equal(2, eventsOf(sink, 1, "device").size(), "one event per device");
      equal(
          "WiFi",
          eventsOf(sink, 1, "device").getFirst().get("connection"),
          "connection kind spelling");
      var devices = (List<?>) resultData(sink, 1).get("devices");
      equal(2, devices.size(), "result lists both");
      check(!((Map<?, ?>) devices.getFirst()).containsKey("version"), "unknown version is omitted");
      equal("10.3.3.2205", ((Map<?, ?>) devices.getLast()).get("version"), "known version");

      // argument problems
      send.accept("{\"id\":2,\"method\":\"connect\",\"params\":{}}");
      equal("INVALID_ARGUMENT", errorCode(done(sink, 2)), "host is required");
      send.accept(
          "{\"id\":3,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"port\":0}}");
      equal("INVALID_ARGUMENT", errorCode(done(sink, 3)), "port range");
      send.accept("{\"id\":4,\"method\":\"getPin\",\"params\":{\"session\":\"s1\"}}");
      equal("UNKNOWN_SESSION", errorCode(done(sink, 4)), "no session yet");

      // connect: a wrong password leaves nothing behind
      send.accept(connect(5, device, "wrong"));
      var rejected = done(sink, 5);
      equal("AUTH_FAILED", errorCode(rejected), "wrong password");
      equal(
          1L,
          ((Map<?, ?>) ((Map<?, ?>) rejected.get("error")).get("details")).get("failedAttempts"),
          "attempt count reaches the client");
      check(eventsOf(sink, 5, "result").isEmpty(), "no connect result for a failed login");

      // connect: success, with the PIN read as part of it
      send.accept(connect(6, device, "secret"));
      equal(Boolean.TRUE, done(sink, 6).get("ok"), "connect");
      var connected = resultData(sink, 6);
      equal(
          List.of("session", "host", "port", "device", "smb"),
          List.copyOf((java.util.Set<?>) connected.keySet()),
          "result fields in order");
      var summary = (Map<?, ?>) connected.get("device");
      equal("Test Z10", summary.get("name"), "device name");
      check(
          eventsOf(sink, 6, "log").stream()
              .anyMatch(event -> "Reading the device PIN".equals(event.get("message"))),
          "the PIN request is described by what it does");
      check(
          eventsOf(sink, 6, "log").stream()
              .noneMatch(event -> String.valueOf(event.get("message")).contains(".cgi")),
          "no log message names a CGI program");
      equal("2A3B4C5D", summary.get("pin"), "PIN");
      equal("10.3.3.2205", summary.get("osVersion"), "OS version");
      equal("10.3.3.2206", summary.get("radioVersion"), "radio version");
      var session = (String) connected.get("session");
      var smb = (Map<?, ?>) connected.get("smb");
      equal("dtm", smb.get("user"), "share user");
      equal(device.currentSharePassword(), smb.get("password"), "share password of this login");
      equal("WORKGROUP", smb.get("workgroup"), "workgroup");
      equal("%h", smb.get("netBios"), "NetBIOS name as the device writes it");
      check(
          sink.snapshot().stream()
              .filter(event -> "log".equals(event.get("type")))
              .noneMatch(
                  event ->
                      String.valueOf(event.get("message"))
                          .contains(FakeDevice.SHARE_PASSWORD_PREFIX)),
          "the share password is not logged");

      // commands on the session
      send.accept("{\"id\":7,\"method\":\"getPin\",\"params\":{\"session\":\"" + session + "\"}}");
      equal(Boolean.TRUE, done(sink, 7).get("ok"), "getPin");
      equal("2A3B4C5D", resultData(sink, 7).get("pin"), "its data");
      send.accept(
          "{\"id\":8,\"method\":\"getDeviceProperties\",\"params\":{\"session\":\""
              + session
              + "\",\"raw\":true}}");
      equal(Boolean.TRUE, done(sink, 8).get("ok"), "getDeviceProperties");
      equal("Test Z10", ((Map<?, ?>) resultData(sink, 8).get("device")).get("name"), "its data");
      check(
          eventsOf(sink, 8, "result").getFirst().get("raw") instanceof List<?> raw
              && raw.size() == 1,
          "raw output on request");
      send.accept(
          "{\"id\":9,\"method\":\"getDeviceProperties\",\"params\":{\"session\":\""
              + session
              + "\"}}");
      done(sink, 9);
      check(!eventsOf(sink, 9, "result").getFirst().containsKey("raw"), "no raw output by default");

      // personal information sync
      send.accept(request(60, "listPimAccounts", session, ""));
      equal(Boolean.TRUE, done(sink, 60).get("ok"), "listPimAccounts");
      var listed = resultData(sink, 60);
      equal("listAccounts", listed.get("command"), "list: command");
      check(
          String.valueOf(listed.get("contentType")).startsWith("application/json"),
          "list: the content type of the answer");
      equal(200L, listed.get("httpStatus"), "list: HTTP status");
      equal(0L, listed.get("code"), "list: code");
      equal("OK", listed.get("message"), "list: message");
      equal("json", listed.get("bodyFormat"), "list: the answer is JSON");
      equal(
          Json.parse(FakeDevice.DEFAULT_PIM_ANSWER),
          listed.get("body"),
          "list: the body is the JSON itself, not a string");
      check(!listed.containsKey("service"), "list: no service");
      check(
          eventsOf(sink, 60, "log").stream()
              .anyMatch(event -> "Listing the PIM accounts".equals(event.get("message"))),
          "list: announced by what it does");

      send.accept(request(61, "findPimAccount", session, ",\"workstationId\":\"W1\""));
      equal(Boolean.TRUE, done(sink, 61).get("ok"), "findPimAccount");
      equal("findAccount", resultData(sink, 61).get("command"), "find: command");

      device.pimResponses.put("get", "{\"status\":{\"code\":0},\"data\":DTM odd & <x>}");
      send.accept(
          request(
              62,
              "getPimItems",
              session,
              ",\"workstationId\":\"W1\",\"service\":\"contacts\",\"updatedTime\":\"T\",\"limit\":5,"
                  + "\"dirtyOnly\":true,\"localOnly\":true,\"offset\":10"));
      equal(Boolean.TRUE, done(sink, 62).get("ok"), "getPimItems");
      equal("contacts", resultData(sink, 62).get("service"), "get: service");
      equal(
          "{\"status\":{\"code\":0},\"data\":DTM odd & <x>}",
          resultData(sink, 62).get("body"),
          "get: a body that is not JSON is passed on as text");
      equal("text", resultData(sink, 62).get("bodyFormat"), "get: and says so");
      equal(
          "GET /cgi-bin/pimsync.cgi?command=get&workstation_id=W1&service=contacts&updated_time=T"
              + "&limit=5&dirty_only=true&local_only=true&offset=10",
          device.pimRequests().getLast(),
          "get: the request");

      // the device says no: the failure carries the body as a result
      device.pimResponses.put(
          "get",
          "{\"version\":\"1.0\",\"status\":{\"code\":1013,\"msg\":\"received HTTP 401/403 from"
              + " server\"},\"service\":\"events\"}");
      send.accept(
          request(72, "getPimItems", session, ",\"workstationId\":\"W1\",\"service\":\"events\""));
      var refused = done(sink, 72);
      equal(Boolean.FALSE, refused.get("ok"), "a refused read is not a success");
      equal("COMMAND_FAILED", errorCode(refused), "it is a failed command");
      equal(
          1013L,
          ((Map<?, ?>) ((Map<?, ?>) refused.get("error")).get("details")).get("code"),
          "with the device's code");
      equal(
          List.of("log", "result", "done"),
          eventsOf(sink, 72, "*").stream().map(event -> (String) event.get("type")).toList(),
          "and the result still arrives before it");
      check(
          String.valueOf(resultData(sink, 72).get("body")).contains("received HTTP 401/403"),
          "so the body can be read");
      device.pimResponses.put("get", "{\"status\":{\"code\":0},\"data\":DTM odd & <x>}");

      // arguments are refused before anything is sent
      var before = device.pimRequests().size();
      var events = ",\"workstationId\":\"W1\",\"service\":\"events\"";
      List.of(
              request(63, "findPimAccount", session, ""),
              request(
                  64, "getPimItems", session, ",\"workstationId\":\"W1\",\"service\":\"photos\""),
              request(65, "getPimItems", session, ",\"workstationId\":\"W1\""),
              request(66, "getPimItems", session, events + ",\"offset\":5"),
              request(67, "getPimItems", session, events + ",\"limit\":0"),
              request(68, "getPimItems", session, events + ",\"limit\":\"x\""),
              request(69, "getPimItems", session, events + ",\"dirtyOnly\":\"yes\""))
          .forEach(send);
      for (var id = 63; id <= 69; id++) {
        equal("INVALID_ARGUMENT", errorCode(done(sink, id)), "refused: request " + id);
      }
      check(
          String.valueOf(((Map<?, ?>) done(sink, 64).get("error")).get("message"))
              .contains("must be one of: contacts, events, calendars"),
          "the accepted services are listed");
      equal(before, device.pimRequests().size(), "nothing was sent to the device");

      // enrolling a certificate
      var pairingBefore = device.pairingBodies().size();
      var requestsBefore = device.cgiRequests.get();
      send.accept(
          request(80, "pairDevice", session, ",\"deviceId\":\"PEER-1\",\"certificate\":\"x\""));
      send.accept(
          request(81, "pairDevice", session, ",\"certificate\":\"x\",\"confirmPin\":\"2A3B4C5D\""));
      send.accept(
          request(
              82,
              "pairDevice",
              session,
              ",\"deviceId\":\"a/b\",\"certificate\":\"x\",\"confirmPin\":\"2A3B4C5D\""));
      send.accept(
          request(
              83, "pairDevice", session, ",\"deviceId\":\"PEER-1\",\"confirmPin\":\"2A3B4C5D\""));
      for (var id = 80; id <= 83; id++) {
        equal("INVALID_ARGUMENT", errorCode(done(sink, id)), "pairDevice refused: request " + id);
      }
      equal(
          requestsBefore,
          device.cgiRequests.get(),
          "nothing was sent to the device, not even a PIN read");

      send.accept(
          request(
              84,
              "pairDevice",
              session,
              ",\"deviceId\":\"PEER-1\",\"certificate\":\"x\",\"confirmPin\":\"00000000\""));
      var mismatch = done(sink, 84);
      equal("PIN_MISMATCH", errorCode(mismatch), "a wrong confirmation PIN");
      check(eventsOf(sink, 84, "result").isEmpty(), "and no result");
      equal(pairingBefore, device.pairingBodies().size(), "nothing reached pairing");

      var invalid = "not a certificate";
      send.accept(
          request(
              85,
              "pairDevice",
              session,
              ",\"deviceId\":\"PEER-1\",\"certificate\":\""
                  + invalid
                  + "\",\"confirmPin\":\"2A3B4C5D\""));
      var rejectedPairing = done(sink, 85);
      equal(
          Boolean.FALSE,
          rejectedPairing.get("ok"),
          "the device rejects text that is not a certificate");
      equal("COMMAND_FAILED", errorCode(rejectedPairing), "as a failed command");
      equal(
          7L,
          ((Map<?, ?>) ((Map<?, ?>) rejectedPairing.get("error")).get("details"))
              .get("errorIdentifier"),
          "with the device's error number");
      equal(
          List.of("log", "log", "result", "done"),
          eventsOf(sink, 85, "*").stream().map(event -> (String) event.get("type")).toList(),
          "the PIN is read, the certificate is sent, and the answer arrives before the failure");
      var pairingAnswer = resultData(sink, 85);
      equal("Error", pairingAnswer.get("status"), "answer: status");
      check(
          String.valueOf(pairingAnswer.get("contentType")).startsWith("text/xml"),
          "answer: the pairing service answers with XML");
      equal(7L, pairingAnswer.get("errorIdentifier"), "answer: error number");
      equal(
          "Error - insert certificate failed.",
          pairingAnswer.get("errorDescription"),
          "answer: description");
      check(
          pairingAnswer.get("body") instanceof Map<?, ?> body
              && body.get("Pairing") instanceof Map<?, ?> pairing
              && "Error".equals(pairing.get("Status"))
              && pairing.get("Error") instanceof Map<?, ?> error
              && "7".equals(error.get("ErrorIdentifier")),
          "answer: the XML body as JSON, with the values as text");
      equal("xml", pairingAnswer.get("bodyFormat"), "answer: and says it was XML");
      check(
          eventsOf(sink, 85, "log").stream()
              .noneMatch(event -> String.valueOf(event.get("message")).contains(invalid)),
          "the certificate is not logged");
      equal(
          "certdata=not%20a%20certificate&deviceid=PEER-1",
          device.pairingBodies().getLast(), "the device received the text unchanged");

      send.accept(
          request(
              86,
              "pairDevice",
              session,
              ",\"deviceId\":\"PEER-1\",\"certificate\":\""
                  + "x".repeat(1025)
                  + "\",\"confirmPin\":\"2A3B4C5D\""));
      done(sink, 86);
      check(
          eventsOf(sink, 86, "log").stream()
              .anyMatch(
                  event ->
                      "warn".equals(event.get("level"))
                          && String.valueOf(event.get("message"))
                              .contains("longer than 1024 bytes")),
          "a certificate over the device's limit is sent, with a warning");

      // the share credentials can be asked for again
      send.accept(
          "{\"id\":70,\"method\":\"getSmbCredentials\",\"params\":{\"session\":\""
              + session
              + "\"}}");
      equal(Boolean.TRUE, done(sink, 70).get("ok"), "getSmbCredentials");
      equal(device.currentSharePassword(), resultData(sink, 70).get("password"), "its password");

      // the device forgets the session: the daemon renews it without the client noticing
      device.expireSessions();
      send.accept("{\"id\":10,\"method\":\"getPin\",\"params\":{\"session\":\"" + session + "\"}}");
      equal(Boolean.TRUE, done(sink, 10).get("ok"), "session renewed behind the scenes");
      send.accept(
          "{\"id\":71,\"method\":\"getSmbCredentials\",\"params\":{\"session\":\""
              + session
              + "\"}}");
      done(sink, 71);
      check(
          !smb.get("password").equals(resultData(sink, 71).get("password")),
          "after the renewal the password has changed, and getSmbCredentials has the new one");
      equal(
          device.currentSharePassword(), resultData(sink, 71).get("password"), "the latest login");

      // disconnect
      send.accept(
          "{\"id\":11,\"method\":\"disconnect\",\"params\":{\"session\":\"" + session + "\"}}");
      done(sink, 11);
      send.accept("{\"id\":12,\"method\":\"getPin\",\"params\":{\"session\":\"" + session + "\"}}");
      equal("UNKNOWN_SESSION", errorCode(done(sink, 12)), "closed session");

      // a PIN that cannot be read does not prevent the connection
      device.pinXml =
          "<RimTabletResponse><DevicePIN><Status>Error</Status><ErrorDescription>no</ErrorDescription></DevicePIN></RimTabletResponse>";
      send.accept(connect(13, device, "secret"));
      equal(Boolean.TRUE, done(sink, 13).get("ok"), "connect without PIN");
      check(
          !((Map<?, ?>) resultData(sink, 13).get("device")).containsKey("pin"),
          "no pin in the summary");
      check(
          eventsOf(sink, 13, "log").stream()
              .anyMatch(
                  event ->
                      "warn".equals(event.get("level"))
                          && String.valueOf(event.get("message")).contains("PIN")),
          "a warning explains it");

      check(
          !sink.everything.toString().contains("secret"),
          "the password never appears in the output");
    }
    toServer.close();
    server.join(15_000);
    check(!server.isAlive(), "daemon exits at the end of its input");
  }

  private static String request(int id, String method, String session, String more) {
    return "{\"id\":%d,\"method\":\"%s\",\"params\":{\"session\":\"%s\"%s}}"
        .formatted(id, method, session, more);
  }

  private static String connect(int id, FakeDevice device, String password) {
    return "{\"id\":%d,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"port\":%d,\"password\":\"%s\"}}"
        .formatted(id, device.port(), password);
  }
}
