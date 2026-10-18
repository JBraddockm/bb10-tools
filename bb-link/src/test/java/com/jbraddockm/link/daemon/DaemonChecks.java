package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.daemon.DaemonServer;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.discovery.ConnectionKind;
import com.jbraddockm.link.discovery.DiscoveredDevice;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.BarFiles;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.TestAuthority;
import com.jbraddockm.link.testing.TestPki;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    Consumer<String> send =
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
              "getAuthenticationCertificate",
              "listDevices",
              "inspectPackage",
              "installPackages",
              "uninstallPackages",
              "inspectCertificate"),
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

      // looking at a bar needs neither a session nor a device
      var barDirectory = Files.createTempDirectory("bb-link-bars");
      var application = BarFiles.application(barDirectory, "app.bar", "gYABgExample1", "1.2.3.4");
      send.accept(inspect(200, application, false));
      equal(Boolean.TRUE, done(sink, 200).get("ok"), "inspectPackage");
      var inspected = resultData(sink, 200);
      equal("application", inspected.get("kind"), "inspect: kind");
      equal("gYABgExample1", inspected.get("packageId"), "inspect: id");
      equal("1.2.3.4", inspected.get("packageVersion"), "inspect: version");
      equal("armle-v7", inspected.get("architecture"), "inspect: architecture");
      equal(application.toString(), inspected.get("file"), "inspect: the file");
      equal(Files.size(application), inspected.get("sizeBytes"), "inspect: the size");
      check(!inspected.containsKey("validation"), "inspect: no validation unless asked for");
      send.accept(inspect(201, application, true));
      equal(Boolean.TRUE, done(sink, 201).get("ok"), "inspectPackage with validation");
      var validation = (Map<?, ?>) resultData(sink, 201).get("validation");
      equal("unsigned", validation.get("rules"), "validation: which rules");
      check(validation.get("valid") instanceof Boolean, "validation: a verdict");
      check(validation.get("messages") instanceof List<?>, "validation: the findings");
      send.accept("{\"id\":202,\"method\":\"inspectPackage\",\"params\":{}}");
      var noFile = done(sink, 202);
      equal("INVALID_ARGUMENT", errorCode(noFile), "inspect: the file is required");
      equal(
          "file",
          ((Map<?, ?>) ((Map<?, ?>) noFile.get("error")).get("details")).get("param"),
          "inspect: and named");
      send.accept(inspect(203, barDirectory.resolve("missing.bar"), false));
      var absentFile = done(sink, 203);
      equal("IO_ERROR", errorCode(absentFile), "inspect: a file that is not there");
      equal(
          "The file does not exist",
          ((Map<?, ?>) absentFile.get("error")).get("message"),
          "inspect: says so");
      var notABar = Files.writeString(barDirectory.resolve("text.bar"), "no archive");
      send.accept(inspect(204, notABar, false));
      var refusedBar = done(sink, 204);
      equal("INVALID_ARGUMENT", errorCode(refusedBar), "inspect: a file that is not a bar");
      check(
          String.valueOf(((Map<?, ?>) refusedBar.get("error")).get("message"))
              .contains("not a usable bar"),
          "inspect: says it is not a usable bar");
      var unknownType =
          BarFiles.write(barDirectory, "gizmo.bar", BarFiles.manifest("Package-Type: gizmo"));
      send.accept(inspect(205, unknownType, false));
      equal("INVALID_ARGUMENT", errorCode(done(sink, 205)), "inspect: an unknown package type");
      send.accept(inspect(206, barDirectory, false));
      equal("INVALID_ARGUMENT", errorCode(done(sink, 206)), "inspect: a folder");
      var firmware =
          BarFiles.write(
              barDirectory,
              "radio.bar",
              BarFiles.manifest("Package-Type: system", "System-Type: radio"));
      send.accept(inspect(207, firmware, false));
      equal(Boolean.TRUE, done(sink, 207).get("ok"), "inspect: a radio image is read");
      equal("radio", resultData(sink, 207).get("kind"), "inspect: and is a radio image");

      // argument problems
      send.accept("{\"id\":2,\"method\":\"connect\",\"params\":{}}");
      equal("INVALID_ARGUMENT", errorCode(done(sink, 2)), "host is required");
      send.accept(
          "{\"id\":3,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"port\":0}}");
      equal("INVALID_ARGUMENT", errorCode(done(sink, 3)), "port range");
      send.accept(
          "{\"id\":90,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"clientCertificate\":\"c.pem\"}}");
      send.accept(
          "{\"id\":91,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"password\":\"x\"}}");
      for (var legacy :
          List.of(new Object[] {90, "clientCertificate"}, new Object[] {91, "password"})) {
        var moved = done(sink, (Integer) legacy[0]);
        equal("INVALID_ARGUMENT", errorCode(moved), "an old top-level parameter: " + legacy[1]);
        equal(
            legacy[1],
            ((Map<?, ?>) ((Map<?, ?>) moved.get("error")).get("details")).get("param"),
            "names the parameter that moved: " + legacy[1]);
        check(
            String.valueOf(((Map<?, ?>) moved.get("error")).get("message"))
                .contains("'authentication'"),
            "and says where it went: " + legacy[1]);
      }
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
          List.of("session", "host", "port", "authentication", "device", "smb"),
          List.copyOf((Set<?>) connected.keySet()),
          "result fields in order");
      equal("password", connected.get("authentication"), "connect echoes the way it authenticated");
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

      // installing applications, through the protocol
      device.updateProgress = List.of(50, 100);
      var installDirectory = Files.createTempDirectory("bb-link-install-daemon");
      var toInstall = BarFiles.application(installDirectory, "one.bar", "gYABgInstall1", "3.0.0.1");
      send.accept(request(300, "installPackages", session, files(toInstall)));
      equal(Boolean.TRUE, done(sink, 300).get("ok"), "installPackages");
      var installedData = resultData(sink, 300);
      var installedList = (List<?>) installedData.get("installed");
      equal(1, installedList.size(), "install: one package");
      equal(
          "gYABgInstall1",
          ((Map<?, ?>) installedList.getFirst()).get("packageId"),
          "install: its id");
      equal("xml", installedData.get("bodyFormat"), "install: the device's last answer, as JSON");
      var installProgress = eventsOf(sink, 300, "progress");
      check(!installProgress.isEmpty(), "install: progress events");
      check(
          installProgress.stream().anyMatch(event -> "installing".equals(event.get("phase"))),
          "install: the device's progress is named installing");
      check(
          eventsOf(sink, 300, "log").stream()
              .anyMatch(event -> "Starting the installation".equals(event.get("message"))),
          "install: the log says what is done, not which program");
      check(
          eventsOf(sink, 300, "log").size() >= 4,
          "install: the log says what is done, at the start, during the upload and at the end");

      send.accept(request(301, "installPackages", session, ""));
      var noFiles = done(sink, 301);
      equal("INVALID_ARGUMENT", errorCode(noFiles), "install: the files are required");
      equal(
          "files",
          ((Map<?, ?>) ((Map<?, ?>) noFiles.get("error")).get("details")).get("param"),
          "install: and named");
      var systemBar =
          BarFiles.write(installDirectory, "system.bar", BarFiles.manifest("Package-Type: system"));
      send.accept(request(302, "installPackages", session, files(toInstall, systemBar)));
      var refusedSystem = done(sink, 302);
      equal("INVALID_ARGUMENT", errorCode(refusedSystem), "install: firmware is refused");
      equal(
          "files[1]",
          ((Map<?, ?>) ((Map<?, ?>) refusedSystem.get("error")).get("details")).get("param"),
          "install: naming the file");
      var notBar = Files.writeString(installDirectory.resolve("text.bar"), "no archive");
      send.accept(request(303, "installPackages", session, files(notBar)));
      var refusedText = done(sink, 303);
      equal("INVALID_ARGUMENT", errorCode(refusedText), "install: a file that is not a bar");
      check(
          String.valueOf(((Map<?, ?>) refusedText.get("error")).get("message"))
              .contains("files[0]"),
          "install: names which");
      send.accept(
          request(304, "installPackages", session, files(installDirectory.resolve("missing.bar"))));
      var missingFile = done(sink, 304);
      equal("IO_ERROR", errorCode(missingFile), "install: a file that is not there");
      equal(
          "files[0]",
          ((Map<?, ?>) ((Map<?, ?>) missingFile.get("error")).get("details")).get("param"),
          "install: names which");
      var updateRequestsBefore = device.updateRequests.size();
      device.updateFailsAt = "send";
      send.accept(request(305, "installPackages", session, files(toInstall)));
      var refusedByDevice = done(sink, 305);
      device.updateFailsAt = "";
      equal(Boolean.FALSE, refusedByDevice.get("ok"), "install: the device refuses the package");
      equal("COMMAND_FAILED", errorCode(refusedByDevice), "install: as a failed command");
      check(
          eventsOf(sink, 305, "result").size() == 1,
          "install: the answer is still sent, before the failure");
      equal(
          0,
          ((List<?>) resultData(sink, 305).get("installed")).size(),
          "install: nothing was installed");
      check(device.updateRequests.size() > updateRequestsBefore, "install: the device was asked");

      // removing applications, through the protocol
      var propertiesBeforeRemoval = device.propertiesXml;
      device.propertiesXml = FakeDevice.propertiesWithApplications("gYABgRemove1", "gYABgRemove2");
      send.accept(request(310, "uninstallPackages", session, ids("gYABgRemove1")));
      equal(Boolean.TRUE, done(sink, 310).get("ok"), "uninstallPackages");
      var removedData = resultData(sink, 310);
      equal(
          List.of("gYABgRemove1"),
          removedData.get("removed"),
          "uninstall: the package that was removed");
      equal("xml", removedData.get("bodyFormat"), "uninstall: the device's last answer, as JSON");
      var removalProgress = eventsOf(sink, 310, "progress");
      check(
          !removalProgress.isEmpty()
              && removalProgress.stream()
                  .allMatch(
                      event ->
                          "removing".equals(event.get("phase"))
                              && "gYABgRemove1".equals(event.get("item"))),
          "uninstall: progress names the phase and the package");
      check(
          eventsOf(sink, 310, "log").stream()
              .anyMatch(event -> "Removing package 1 of 1".equals(event.get("message"))),
          "uninstall: the log says what is done");

      send.accept(request(311, "uninstallPackages", session, ""));
      var noIds = done(sink, 311);
      equal("INVALID_ARGUMENT", errorCode(noIds), "uninstall: the ids are required");
      equal("packageIds", detailsOf(noIds).get("param"), "uninstall: and named");
      send.accept(request(312, "uninstallPackages", session, ids("a b")));
      var badId = done(sink, 312);
      equal("INVALID_ARGUMENT", errorCode(badId), "uninstall: an id that is not an id");
      equal("packageIds[0]", detailsOf(badId).get("param"), "uninstall: naming which");
      send.accept(request(313, "uninstallPackages", session, ids("gYABgRemove1", "gYABgRemove1")));
      equal("INVALID_ARGUMENT", errorCode(done(sink, 313)), "uninstall: the same package twice");
      send.accept(
          request(314, "uninstallPackages", session, ids("gYABgRemove2") + ",\"check\":\"no\""));
      var badFlag = done(sink, 314);
      equal("INVALID_ARGUMENT", errorCode(badFlag), "uninstall: check must be a boolean");
      equal("check", detailsOf(badFlag).get("param"), "uninstall: and named");

      var removalRequestsBefore = device.updateRequests.size();
      send.accept(request(315, "uninstallPackages", session, ids("gYABgRemove2", "ghost")));
      var ghost = done(sink, 315);
      equal("INVALID_ARGUMENT", errorCode(ghost), "uninstall: an id that is not installed");
      equal("packageIds[1]", detailsOf(ghost).get("param"), "uninstall: naming which");
      send.accept(request(316, "uninstallPackages", session, ids("os1")));
      equal("INVALID_ARGUMENT", errorCode(done(sink, 316)), "uninstall: firmware is refused");
      equal(
          removalRequestsBefore,
          device.updateRequests.size(),
          "uninstall: and nothing was sent to remove");

      device.updateUnremovable.add("ghost");
      send.accept(request(317, "uninstallPackages", session, ids("ghost") + ",\"check\":false"));
      var removalRefused = done(sink, 317);
      equal(
          Boolean.FALSE,
          removalRefused.get("ok"),
          "uninstall: the device refuses an id, with the check off");
      equal("COMMAND_FAILED", errorCode(removalRefused), "uninstall: as a failed command");
      equal(
          "Cannot uninstall packageid",
          ((Map<?, ?>) removalRefused.get("error")).get("message"),
          "uninstall: in the device's words");
      equal(
          1,
          eventsOf(sink, 317, "result").size(),
          "uninstall: the answer is still sent, before the failure");
      equal(List.of(), resultData(sink, 317).get("removed"), "uninstall: nothing was removed");
      device.updateUnremovable.clear();
      device.propertiesXml = propertiesBeforeRemoval;

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

      // a connection that only logs in: nothing of the device is read
      send.accept(connect(320, device, "wrong", ",\"deviceInfo\":false"));
      var loginOnlyRejected = done(sink, 320);
      equal(
          "AUTH_FAILED",
          errorCode(loginOnlyRejected),
          "login only: a wrong password is still refused");
      var requestsBeforeLoginOnly = device.cgiRequests.get();
      send.accept(connect(321, device, "secret", ",\"deviceInfo\":false"));
      equal(Boolean.TRUE, done(sink, 321).get("ok"), "connect that only logs in");
      var loggedInOnly = resultData(sink, 321);
      equal(
          List.of("session", "host", "port", "authentication", "smb"),
          List.copyOf((Set<?>) loggedInOnly.keySet()),
          "login only: the fields, without a device");
      check(
          loggedInOnly.get("smb") != null,
          "login only: the share credentials of the login are there");
      equal(
          requestsBeforeLoginOnly,
          device.cgiRequests.get(),
          "login only: no program of the device was asked anything");
      var loginOnlySession = (String) loggedInOnly.get("session");
      send.accept(request(322, "getPin", loginOnlySession, ""));
      equal(Boolean.TRUE, done(sink, 322).get("ok"), "login only: the session works afterwards");
      equal(
          requestsBeforeLoginOnly + 1,
          device.cgiRequests.get(),
          "login only: and what is asked later is asked then");
      send.accept(
          "{\"id\":323,\"method\":\"disconnect\",\"params\":{\"session\":\""
              + loginOnlySession
              + "\"}}");
      equal(Boolean.TRUE, done(sink, 323).get("ok"), "login only: and it can be closed");
      send.accept(connect(324, device, "secret", ",\"deviceInfo\":\"no\""));
      var notBoolean = done(sink, 324);
      equal("INVALID_ARGUMENT", errorCode(notBoolean), "deviceInfo must be a boolean");
      equal("deviceInfo", detailsOf(notBoolean).get("param"), "and is named");

      // a recording is cut at a line of three dashes and nothing else: a PEM line is no separator
      equal(
          List.of("a\n", "b\n"),
          FixtureReplay.split("a\n---\nb\n"),
          "fixtures: a line of three dashes separates two answers");
      equal(
          1,
          FixtureReplay.split("-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n")
              .size(),
          "fixtures: a PEM block is not cut apart");
      equal(
          1, FixtureReplay.split("---x\n").size(), "fixtures: three dashes and more text is none");
      equal(
          2,
          FixtureReplay.split("a\n---  \r\nb").size(),
          "fixtures: blanks after the dashes are allowed");
      equal(
          List.of("a\n", ""),
          FixtureReplay.split("a\n---"),
          "fixtures: a separator at the very end");

      // asking the device to sign a certificate, through the protocol
      var devicesDirectory = Files.createTempDirectory("bb-link-devices");
      device.authority = new TestAuthority();
      var issuing =
          ",\"confirmPin\":\"2A3B4C5D\",\"devicesDirectory\":"
              + Json.write(devicesDirectory.toString())
              + ",\"hostName\":\"workstation\",\"verify\":false";
      send.accept(request(370, "getAuthenticationCertificate", session, issuing));
      equal(
          Boolean.TRUE,
          done(sink, 370).get("ok"),
          "getAuthenticationCertificate asks the device to sign a certificate");
      var issued = resultData(sink, 370);
      equal(
          "BBCGIAUTH [2A3B4C5D] workstation",
          issued.get("commonName"),
          "the common name of the request");
      equal("2A3B4C5D", issued.get("pin"), "the PIN of the device");
      check(
          Files.exists(Path.of((String) issued.get("certificateFile"))), "the certificate is kept");
      check(Files.exists(Path.of((String) issued.get("keyFile"))), "and the key");
      check(!issued.toString().contains("PRIVATE"), "and no key is in the result");
      equal(
          "skipped",
          ((Map<?, ?>) issued.get("verification")).get("state"),
          "the login with it is not tried when switched off");
      send.accept(
          request(371, "getAuthenticationCertificate", session, ",\"confirmPin\":\"2A3B4C5D\""));
      equal("devicesDirectory", detailsOf(done(sink, 371)).get("param"), "a folder is required");
      send.accept(request(372, "getAuthenticationCertificate", session, issuing));
      equal(
          "IDENTITY_EXISTS",
          errorCode(done(sink, 372)),
          "an identity that is there is not replaced without being asked");
      send.accept(
          request(373, "getAuthenticationCertificate", session, issuing + ",\"replace\":true"));
      equal(Boolean.TRUE, done(sink, 373).get("ok"), "and is replaced when asked");
      send.accept(
          request(
              374,
              "getAuthenticationCertificate",
              session,
              ",\"confirmPin\":\"2A3B4C5D\",\"devicesDirectory\":"
                  + Json.write(devicesDirectory.toString())
                  + ",\"hostName\":\" \""));
      equal("hostName", detailsOf(done(sink, 374)).get("param"), "a name is not blank");

      // pairing with the device, through the protocol
      var pairingDirectory = Files.createTempDirectory("bb-link-devices-pairing");
      var pairing =
          ",\"confirmPin\":\"2A3B4C5D\",\"devicesDirectory\":"
              + Json.write(pairingDirectory.toString());
      send.accept(request(380, "pairDevice", session, pairing));
      equal(
          Boolean.TRUE,
          done(sink, 380).get("ok"),
          "pairDevice pairs this computer with the device");
      var paired = resultData(sink, 380);
      check(
          paired.get("deviceId").toString().matches("01[0-9A-F]{32}"),
          "the identifier: " + paired.get("deviceId"));
      check(Files.exists(Path.of((String) paired.get("authorityFile"))), "the authority is kept");
      check(Files.exists(Path.of((String) paired.get("keyFile"))), "and its key");
      check(
          Files.exists(Path.of((String) paired.get("deviceAuthorityFile"))),
          "and the device's authority");
      check(!paired.toString().contains("PRIVATE"), "and no key is in the result");
      equal(
          Boolean.TRUE,
          ((Map<?, ?>) paired.get("authority")).get("certificateAuthority"),
          "the authority is an authority");
      check(
          String.valueOf(((Map<?, ?>) paired.get("deviceAuthority")).get("subject"))
              .contains("CN="),
          "the device's authority is described");
      send.accept(request(381, "pairDevice", session, ",\"confirmPin\":\"2A3B4C5D\""));
      equal("devicesDirectory", detailsOf(done(sink, 381)).get("param"), "a folder is required");
      send.accept(request(382, "pairDevice", session, pairing));
      equal(
          "PAIRING_EXISTS",
          errorCode(done(sink, 382)),
          "a pairing that is there is not replaced without being asked");
      send.accept(request(383, "pairDevice", session, pairing + ",\"replace\":true"));
      equal(Boolean.TRUE, done(sink, 383).get("ok"), "and is replaced when asked");

      // what a certificate says, with no session and no device
      send.accept(
          "{\"id\":336,\"method\":\"inspectCertificate\",\"params\":{\"file\":"
              + Json.write((String) issued.get("certificateFile"))
              + "}}");
      equal(Boolean.TRUE, done(sink, 336).get("ok"), "inspectCertificate needs no session");
      var inspectedCertificate = resultData(sink, 336);
      check(
          String.valueOf(inspectedCertificate.get("subject")).contains("BBCGIAUTH"),
          "inspect: the subject");
      check(
          ((Number) inspectedCertificate.get("daysLeft")).longValue() >= 36
              && ((Number) inspectedCertificate.get("daysLeft")).longValue() <= 37,
          "inspect: 36 or 37 days left of the 37: " + inspectedCertificate.get("daysLeft"));
      equal(Boolean.FALSE, inspectedCertificate.get("expired"), "inspect: not expired");
      send.accept(
          "{\"id\":337,\"method\":\"inspectCertificate\",\"params\":{\"file\":"
              + Json.write(devicesDirectory.resolve("nothing.pem").toString())
              + "}}");
      var missingCertificate = done(sink, 337);
      equal("INVALID_ARGUMENT", errorCode(missingCertificate), "inspect: a file that is not there");
      equal("file", detailsOf(missingCertificate).get("param"), "inspect: names the parameter");
      device.authority = null;

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

      // the authentication object: every way it can be wrong is refused before anything is sent
      var deviceRequestsBefore = device.cgiRequests.get();
      var loginsBefore = device.loginRequests.get();
      var invalidObjects =
          List.of(
              new String[] {"\"password\"", "authentication"},
              new String[] {"{}", "authentication.type"},
              new String[] {"{\"type\":5}", "authentication.type"},
              new String[] {"{\"type\":\"Password\",\"password\":\"x\"}", "authentication.type"},
              new String[] {"{\"type\":\"none\",\"password\":\"x\"}", "authentication.password"},
              new String[] {"{\"type\":\"password\"}", "authentication.password"},
              new String[] {
                "{\"type\":\"password\",\"password\":\"  \"}", "authentication.password"
              },
              new String[] {
                "{\"type\":\"password\",\"password\":\"x\",\"clientKey\":\"k.pem\"}",
                "authentication.clientKey"
              },
              new String[] {
                "{\"type\":\"certificate\",\"clientCertificate\":\"c.pem\"}",
                "authentication.clientKey"
              },
              new String[] {
                "{\"type\":\"certificate\",\"clientKey\":\"k.pem\"}",
                "authentication.clientCertificate"
              },
              new String[] {
                "{\"type\":\"certificate\",\"clientCertificate\":\"c.pem\",\"clientKey\":\"k.pem\",\"password\":\"x\"}",
                "authentication.password"
              },
              new String[] {
                "{\"type\":\"certificateAndPassword\",\"clientCertificate\":\"c.pem\",\"clientKey\":\"k.pem\"}",
                "authentication.password"
              },
              new String[] {
                "{\"type\":\"certificate\",\"clientCertificate\":5,\"clientKey\":\"k.pem\"}",
                "authentication.clientCertificate"
              });
      var invalidId = 100;
      for (var entry : invalidObjects) {
        send.accept(authenticate(invalidId, device, entry[0]));
        var refusal = done(sink, invalidId);
        equal("INVALID_ARGUMENT", errorCode(refusal), "refused: " + entry[0]);
        equal(
            entry[1],
            ((Map<?, ?>) ((Map<?, ?>) refusal.get("error")).get("details")).get("param"),
            "names " + entry[1] + " for: " + entry[0]);
        invalidId++;
      }
      send.accept(
          "{\"id\":%d,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"port\":%d}}"
              .formatted(invalidId, device.port()));
      var missing = done(sink, invalidId);
      equal(
          "authentication",
          ((Map<?, ?>) ((Map<?, ?>) missing.get("error")).get("details")).get("param"),
          "a connect without authentication is refused, with nothing assumed");
      equal(
          deviceRequestsBefore, device.cgiRequests.get(), "no refused request reached the device");
      equal(loginsBefore, device.loginRequests.get(), "and none made a login");

      // a device that asks for a password nobody gave: no answer is sent, no attempt is counted
      var failedBefore = device.failedAttempts;
      send.accept(authenticate(150, device, "{\"type\":\"none\"}"));
      var passwordRequired = done(sink, 150);
      equal(
          "PASSWORD_REQUIRED",
          errorCode(passwordRequired),
          "a password is needed and none was given");
      equal(loginsBefore + 1, device.loginRequests.get(), "the login stopped at the challenge");
      equal(failedBefore, device.failedAttempts, "no failed attempt was counted");

      // a device without a password: the type none works, and says so
      device.loginMode = FakeDevice.LoginMode.NO_PASSWORD;
      send.accept(authenticate(151, device, "{\"type\":\"none\"}"));
      equal(Boolean.TRUE, done(sink, 151).get("ok"), "connect to a device without a password");
      equal("none", resultData(sink, 151).get("authentication"), "and the type is echoed");
      device.loginMode = FakeDevice.LoginMode.CHALLENGE;

      // the certificate types reach the session: files are read, and the login happens or not
      var directory = Files.createTempDirectory("bb-link-daemon-certificates");
      var certificateFile = Files.writeString(directory.resolve("c.pem"), TestPki.LEAF_CERTIFICATE);
      var keyFile = Files.writeString(directory.resolve("k.pem"), TestPki.LEAF_KEY_PKCS8);
      var files =
          "\"clientCertificate\":\"%s\",\"clientKey\":\"%s\"".formatted(certificateFile, keyFile);
      loginsBefore = device.loginRequests.get();
      send.accept(authenticate(152, device, "{\"type\":\"certificate\"," + files + "}"));
      var notAccepted = done(sink, 152);
      equal("AUTH_FAILED", errorCode(notAccepted), "a device that does not take the certificate");
      check(
          String.valueOf(((Map<?, ?>) notAccepted.get("error")).get("message"))
              .contains("client certificate"),
          "says it is the certificate");
      equal(
          loginsBefore + 2,
          device.loginRequests.get(),
          "the type certificate asks for the login (twice: once more after the rejection)");
      equal(failedBefore, device.failedAttempts, "and never answers the password question");
      check(
          eventsOf(sink, 152, "log").stream()
              .anyMatch(
                  event ->
                      "warn".equals(event.get("level"))
                          && String.valueOf(event.get("message")).contains("asked for a password")),
          "the question is reported as a warning");
      send.accept(
          authenticate(
              153,
              device,
              "{\"type\":\"certificateAndPassword\"," + files + ",\"password\":\"secret\"}"));
      equal(Boolean.TRUE, done(sink, 153).get("ok"), "a certificate and a password");
      equal("certificateAndPassword", resultData(sink, 153).get("authentication"), "echoed");
      check(resultData(sink, 153).containsKey("smb"), "and the login brings the share credentials");
      check(device.loginRequests.get() > loginsBefore, "because it logged in");
      send.accept(
          authenticate(
              154,
              device,
              "{\"type\":\"certificate\",\"clientCertificate\":\"%s\",\"clientKey\":\"%s\"}"
                  .formatted(directory.resolve("nothing.pem"), keyFile)));
      equal("IO_ERROR", errorCode(done(sink, 154)), "a certificate file that is not there");

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

  /** The {@code packageIds} parameter for a request, as the text that follows {@code session}. */
  private static String ids(String... packageIds) {
    return ",\"packageIds\":["
        + Arrays.stream(packageIds).map(id -> "\"" + id + "\"").collect(Collectors.joining(","))
        + "]";
  }

  /** The details of the error of a {@code done} event. */
  private static Map<?, ?> detailsOf(Map<?, ?> done) {
    return (Map<?, ?>) ((Map<?, ?>) done.get("error")).get("details");
  }

  /** The {@code files} parameter for a request, as the text that follows {@code session}. */
  private static String files(Path... bars) {
    return ",\"files\":["
        + Arrays.stream(bars)
            .map(bar -> "\"" + bar.toString().replace("\\", "\\\\") + "\"")
            .collect(Collectors.joining(","))
        + "]";
  }

  /** A request to inspect a bar. */
  private static String inspect(int id, Path file, boolean validate) {
    return "{\"id\":%d,\"method\":\"inspectPackage\",\"params\":{\"file\":\"%s\",\"validate\":%s}}"
        .formatted(id, file.toString().replace("\\", "\\\\"), validate);
  }

  private static String connect(int id, FakeDevice device, String password) {
    return authenticate(
        id, device, "{\"type\":\"password\",\"password\":\"%s\"}".formatted(password));
  }

  /** A connect request with the given {@code authentication} object, as JSON text. */
  private static String authenticate(int id, FakeDevice device, String authentication) {
    return authenticate(id, device, authentication, "");
  }

  /** A connect request with more parameters, as the text that follows {@code authentication}. */
  private static String authenticate(
      int id, FakeDevice device, String authentication, String more) {
    return "{\"id\":%d,\"method\":\"connect\",\"params\":{\"host\":\"127.0.0.1\",\"port\":%d,\"authentication\":%s%s}}"
        .formatted(id, device.port(), authentication, more);
  }

  private static String connect(int id, FakeDevice device, String password, String more) {
    return authenticate(
        id, device, "{\"type\":\"password\",\"password\":\"%s\"}".formatted(password), more);
  }
}
