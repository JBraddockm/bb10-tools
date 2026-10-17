package com.jbraddockm.link.update;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.BarKind;
import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.session.Authentication;
import com.jbraddockm.link.session.DeviceSession;
import com.jbraddockm.link.session.DeviceTarget;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.BarFiles;
import com.jbraddockm.link.testing.FakeDevice;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;

/**
 * Checks the installation of applications against a fake {@code update.cgi}: the whole
 * conversation, every point where the device can refuse, what the progress says, and what is never
 * repeated.
 */
public final class InstallChecks {

  private static final Duration QUICK = Duration.ofMillis(5);

  private InstallChecks() {}

  /** Collects what a command reports. */
  private static final class Events implements CommandListener {
    final List<String> warnings = Collections.synchronizedList(new ArrayList<>());
    final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    final List<Progress> progress = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onLog(LogLevel level, String message) {
      logs.add(message);
      if (level == LogLevel.WARN) {
        warnings.add(message);
      }
    }

    @Override
    public void onProgress(Progress report) {
      progress.add(report);
    }
  }

  private static DeviceSession session(FakeDevice device, ExchangeObserver observer) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            TransportScheme.HTTP,
            new Authentication.Password("secret")),
        observer);
  }

  private static BarInfo inspect(Path file) throws Exception {
    return BarInspector.inspect(file);
  }

  private static CommandOutcome.Failed<InstallResult> failed(
      CommandOutcome<InstallResult> outcome) {
    check(
        outcome instanceof CommandOutcome.Failed<InstallResult>, "should have failed: " + outcome);
    return (CommandOutcome.Failed<InstallResult>) outcome;
  }

  private static InstallResult succeeded(CommandOutcome<InstallResult> outcome) {
    check(
        outcome instanceof CommandOutcome.Succeeded<InstallResult>,
        "should have succeeded: " + outcome);
    return ((CommandOutcome.Succeeded<InstallResult>) outcome).result();
  }

  private static InstallPackages command(BarInfo... bars) {
    return new InstallPackages(List.of(bars), QUICK);
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    var directory = Files.createTempDirectory("bb-link-install");
    var first = inspect(BarFiles.application(directory, "first.bar", "idFirst", "1.0.0.1"));
    var second = inspect(BarFiles.application(directory, "second.bar", "idSecond", "2.0.0.2"));

    // a whole installation of one package
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      var events = new Events();
      var result = succeeded(session.execute(command(first), events));
      equal(1, result.installed().size(), "one package installed");
      equal(
          "idFirst", result.installed().getFirst().packageId(), "and it is the one that was sent");
      equal(
          List.of(
              "start mode=bar&size=" + first.sizeBytes(),
              "upload type=bar bytes=" + first.sizeBytes() + " type=application/octet-stream",
              "poll",
              "poll",
              "poll",
              "finish"),
          device.updateRequests,
          "what the device was asked: start, upload, three polls, finish");
      check(
          device.updateAgents.stream().allMatch("QNXWebClient/1.0"::equals),
          "update.cgi is asked with the agent that BlackBerry's clients use: "
              + device.updateAgents);
      equal("xml", result.toJson().get("bodyFormat"), "the last answer is passed on, as JSON");
      equal(
          List.of(0, 3, 100, 100),
          events.progress.stream().map(Progress::percent).toList(),
          "progress: the start, the two polls that were not done (100 among them), the end");
      check(
          events.progress.stream().allMatch(report -> report.item().equals("first.bar")),
          "progress names the bar");
      equal(
          1L,
          events.logs.stream()
              .filter("Waiting for the device to install the package"::equals)
              .count(),
          "a poll is logged once, not every time");
    }

    // two packages, and the size is the sum
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      var result = succeeded(session.execute(command(first, second), new Events()));
      equal(
          List.of("idFirst", "idSecond"),
          result.installed().stream().map(InstalledPackage::packageId).toList(),
          "two packages, in order");
      equal(
          "start mode=bar&size=" + (first.sizeBytes() + second.sizeBytes()),
          device.updateRequests.getFirst(),
          "the device is told the sum of the sizes");
      equal(
          2L,
          device.updateRequests.stream().filter(line -> line.startsWith("upload")).count(),
          "two uploads");
      equal("finish", device.updateRequests.getLast(), "and one finish, at the end");
    }

    // an upload large enough to report progress, and the trace that shows no content
    var big = inspect(BarFiles.withPayload(directory, "big.bar", "idBig", "1.0", 600 * 1024));
    try (var device = new FakeDevice("secret")) {
      device.updateProgress = List.of(100);
      var traced = Collections.synchronizedList(new ArrayList<String>());
      try (var session = session(device, (line, body, _, _) -> traced.add(line + " | " + body))) {
        var events = new Events();
        succeeded(session.execute(command(big), events));
        var uploads =
            events.progress.stream()
                .filter(report -> report.phase() == UpdatePhase.UPLOADING)
                .toList();
        check(uploads.size() >= 2, "an upload reports progress as it goes: " + uploads.size());
        check(
            uploads.stream()
                .allMatch(
                    report ->
                        report.bytesTotal() == big.sizeBytes() && "big.bar".equals(report.item())),
            "each report has the total and the name");
        equal(
            uploads.stream().map(Progress::percent).sorted().toList(),
            uploads.stream().map(Progress::percent).toList(),
            "and never goes backwards");
        check(uploads.getLast().percent() > 80, "and ends near the end");
        check(
            traced.stream()
                .anyMatch(
                    entry ->
                        entry.contains(
                            "(application/octet-stream, " + big.sizeBytes() + " bytes: big.bar)")),
            "the trace has the size and name of the file, not its content: " + traced);
        check(traced.stream().allMatch(entry -> entry.length() < 400), "and nothing large");
      }
    }

    // the device refuses at each stage; the answer is kept and what was installed is told
    for (var stage : List.of("start", "send", "progress")) {
      try (var device = new FakeDevice("secret");
          var session = session(device, ExchangeObserver.NONE)) {
        device.updateFailsAt = stage;
        var outcome = failed(session.execute(command(first), new Events()));
        equal(
            "COMMAND_FAILED", outcome.error().code().wireName(), "the device refuses at " + stage);
        check(
            outcome.error().details().get("errorIdentifier") instanceof Integer,
            "with its error number at " + stage);
        check(outcome.partialResult().isPresent(), "and the answer is kept at " + stage);
        equal(
            0,
            outcome.partialResult().get().installed().size(),
            "nothing was installed at " + stage);
      }
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updateFailsAt = "start";
      var outcome = failed(session.execute(command(first), new Events()));
      equal("unable to verify app manifest", outcome.error().message(), "the device's own words");
      equal(
          List.of("start mode=bar&size=" + first.sizeBytes()),
          device.updateRequests,
          "and nothing was uploaded");
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updateFailsAt = "finish";
      var outcome = failed(session.execute(command(first), new Events()));
      equal("Cannot finish", outcome.error().message(), "a failure at the very end");
      equal(
          1,
          outcome.partialResult().get().installed().size(),
          "tells that the package was installed");
    }

    // answers that do not fit
    for (var odd : List.of("<RimTabletResponse><Surprise/></RimTabletResponse>", "hello")) {
      try (var device = new FakeDevice("secret");
          var session = session(device, ExchangeObserver.NONE)) {
        device.updateAnswer = odd;
        var outcome = failed(session.execute(command(first), new Events()));
        equal(
            "UNEXPECTED_RESPONSE",
            outcome.error().code().wireName(),
            "an answer that does not fit: " + odd);
        check(
            String.valueOf(outcome.partialResult().get().toJson().get("body"))
                .contains(odd.equals("hello") ? "hello" : "Surprise"),
            "and is kept whole in the partial result: " + odd);
      }
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updatePollAnswer =
          "<RimTabletResponse><UpdateProgress><Status>Sideways</Status></UpdateProgress></RimTabletResponse>";
      var outcome = failed(session.execute(command(first), new Events()));
      equal("UNEXPECTED_RESPONSE", outcome.error().code().wireName(), "a status that is not known");
      equal("Sideways", outcome.error().details().get("status"), "is named");
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updatePollAnswer =
          "<RimTabletResponse><UpdateProgress><Status>Complete</Status><Progress>100</Progress></UpdateProgress></RimTabletResponse>";
      equal(
          1,
          succeeded(session.execute(command(first), new Events())).installed().size(),
          "Complete ends a package like Success");
    }

    // the end carries a status, as a real device's does
    for (var end : List.of("Success", "Complete", "")) {
      try (var device = new FakeDevice("secret");
          var session = session(device, ExchangeObserver.NONE)) {
        device.updateEndStatus = end;
        equal(
            1,
            succeeded(session.execute(command(first), new Events())).installed().size(),
            "an end with the status [" + end + "] is a success");
      }
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updateEndStatus = "Weird";
      var outcome = failed(session.execute(command(first), new Events()));
      equal("UNEXPECTED_RESPONSE", outcome.error().code().wireName(), "an end with another status");
      equal("Weird", outcome.error().details().get("status"), "names the status");
      equal(1, outcome.partialResult().get().installed().size(), "and tells what was installed");
    }

    // the cookie that the device sets arrives already expired, and is never sent back
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      succeeded(session.execute(command(first), new Events()));
      check(
          device.cookiesSeen().stream().noneMatch(cookie -> cookie.contains("update=")),
          "the installation does not depend on the cookie of the device: " + device.cookiesSeen());
    }

    // space
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.propertiesXml = FakeDevice.propertiesWithFreeSpace(first.sizeBytes());
      var outcome = failed(session.execute(command(first), new Events()));
      equal("NOT_ENOUGH_SPACE", outcome.error().code().wireName(), "too little space");
      equal(
          first.sizeBytes() * 3 / 2,
          outcome.error().details().get("requiredBytes"),
          "the space needed is one and a half times the size");
      equal(first.sizeBytes(), outcome.error().details().get("freeBytes"), "against what is free");
      equal(List.of(), device.updateRequests, "and nothing was sent");
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.propertiesXml =
          FakeDevice.propertiesWithFreeSpace(first.sizeBytes() + first.sizeBytes() / 2);
      succeeded(session.execute(command(first), new Events()));
      check(true, "exactly enough space is enough");
    }
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      var events = new Events();
      succeeded(session.execute(command(first), events));
      check(
          events.warnings.stream()
              .anyMatch(text -> text.contains("does not say how much space is free")),
          "a device that does not say how much is free is told in a warning: " + events.warnings);
    }

    // what is never repeated
    var cut = inspect(BarFiles.withPayload(directory, "cut.bar", "idCut", "1.0", 200 * 1024));
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updateDropUploadAfter = 1000;
      var outcome = failed(session.execute(command(cut), new Events()));
      equal("CONNECTION_LOST", outcome.error().code().wireName(), "an upload that is cut off");
      equal(
          1L,
          device.updateRequests.stream().filter(line -> line.startsWith("upload dropped")).count(),
          "is not sent again");
      equal(2, device.updateRequests.size(), "the start and the one upload, nothing more");
    }
    // the whole file arrives and the device says nothing: it may have installed it, so no second
    // try
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      device.updateDropAfterUpload = true;
      var outcome = failed(session.execute(command(first), new Events()));
      equal("CONNECTION_LOST", outcome.error().code().wireName(), "no answer to a whole upload");
      equal(
          List.of(
              "start mode=bar&size=" + first.sizeBytes(),
              "upload received, not answered: bytes=" + first.sizeBytes()),
          device.updateRequests,
          "the upload went out once and was not repeated");
    }

    // a session that the device has forgotten before anything is sent is renewed once
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      equal(2, device.loginRequests.get(), "one login so far: a challenge and an answer");
      device.expireSessions();
      equal(
          1,
          succeeded(session.execute(command(first), new Events())).installed().size(),
          "a forgotten session is renewed");
      equal(4, device.loginRequests.get(), "with one more login");
    }

    // a session lost in the middle ends the command, and the upload is not sent again
    try (var device = new FakeDevice("secret");
        var session = session(device, ExchangeObserver.NONE)) {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      // the third request of the command is the upload; the session is lost as it is answered
      device.afterNextCgiRequest =
          () ->
              device.afterNextCgiRequest =
                  () -> device.afterNextCgiRequest = device::expireSessions;
      var outcome = failed(session.execute(command(first), new Events()));
      equal("AUTH_FAILED", outcome.error().code().wireName(), "a session lost in the middle");
      equal(
          "The device ended the session while the command was running",
          outcome.error().message(),
          "is said plainly");
      equal(
          List.of(
              "start mode=bar&size=" + first.sizeBytes(),
              "upload type=bar bytes=" + first.sizeBytes() + " type=application/octet-stream",
              "no session"),
          device.updateRequests,
          "the upload was sent once, and the next question found the session gone");
    }

    // what is refused before anything is sent
    var radio =
        inspect(
            BarFiles.write(
                directory,
                "radio.bar",
                BarFiles.manifest("Package-Type: system", "System-Type: radio")));
    var system =
        inspect(BarFiles.write(directory, "system.bar", BarFiles.manifest("Package-Type: system")));
    var token =
        inspect(
            BarFiles.write(directory, "token.bar", BarFiles.manifest("Package-Type: debug-token")));
    for (var refused : List.of(radio, system, token)) {
      var failure = refusal(() -> command(refused));
      check(
          failure != null && failure.parameter().equals("files[0]"),
          "refused, naming the file: " + refused.kind());
      check(
          failure
              .getMessage()
              .contains(
                  refused.kind() == BarKind.OTHER
                      ? "cannot be installed this way"
                      : "not supported yet"),
          "and says why: " + failure.getMessage());
    }
    check(refusal(() -> command()).parameter().equals("files"), "no bar at all is refused");
    var duplicate = refusal(() -> command(first, second, first));
    equal(
        "files[2]", duplicate.parameter(), "the same package twice is refused, naming the second");
    check(duplicate.getMessage().contains("files[0]"), "and the first");
    equal(first.sizeBytes() + second.sizeBytes(), command(first, second).totalBytes(), "the total");
    equal(false, command(first).readOnly(), "installing changes the device");

    // the statuses
    equal(
        UpdateStatus.IN_PROGRESS,
        UpdateStatus.of(" inprogress ").orElseThrow(),
        "a status is found without regard to case");
    equal(UpdateStatus.COMPLETE, UpdateStatus.of("COMPLETE").orElseThrow(), "Complete");
    check(
        UpdateStatus.of("Weird").isEmpty() && UpdateStatus.of("").isEmpty(),
        "an unknown status is not guessed");
    equal("bar", UpdateMode.INSTALL_APPLICATIONS.wireName(), "the mode that installs applications");
    equal("os", UpdateMode.INSTALL_OPERATING_SYSTEM.wireName(), "the mode that installs firmware");
    equal("app", UpdateMode.UNINSTALL_APPLICATIONS.wireName(), "the mode that uninstalls");
    equal("radio", UploadKind.RADIO.wireName(), "the radio upload");

    deleteAll(directory);
  }

  private static InvalidArgumentException refusal(Supplier<InstallPackages> creation) {
    try {
      creation.get();
      return null;
    } catch (InvalidArgumentException failure) {
      return failure;
    }
  }

  private static void deleteAll(Path directory) throws IOException {
    try (var files = Files.walk(directory)) {
      files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
    }
  }
}
