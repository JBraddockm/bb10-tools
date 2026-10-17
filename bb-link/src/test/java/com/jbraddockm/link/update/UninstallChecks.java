package com.jbraddockm.link.update;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

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
import com.jbraddockm.link.testing.FakeDevice;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Checks the removal of applications against a fake {@code update.cgi}: the conversation, the check
 * of the ids against the device's package list, every point where the device can refuse, and what
 * is never repeated.
 */
public final class UninstallChecks {

  private static final String FIRST = "testRel_le_FirstApp0001";
  private static final String SECOND = "testRel_le_SecondApp0002";

  private UninstallChecks() {}

  /** Collects what a command reports. */
  private static final class Events implements CommandListener {
    final List<String> logs = Collections.synchronizedList(new ArrayList<>());
    final List<Progress> progress = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void onLog(LogLevel level, String message) {
      logs.add(message);
    }

    @Override
    public void onProgress(Progress report) {
      progress.add(report);
    }
  }

  private static DeviceSession session(FakeDevice device) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            InetAddress.getLoopbackAddress().getHostAddress(),
            device.port(),
            TransportScheme.HTTP,
            new Authentication.Password("secret")),
        ExchangeObserver.NONE);
  }

  private static FakeDevice deviceWithApplications() throws IOException {
    var device = new FakeDevice("secret");
    device.propertiesXml = FakeDevice.propertiesWithApplications(FIRST, SECOND);
    return device;
  }

  private static CommandOutcome.Failed<UninstallResult> failed(
      CommandOutcome<UninstallResult> outcome) {
    check(
        outcome instanceof CommandOutcome.Failed<UninstallResult>,
        "should have failed: " + outcome);
    return (CommandOutcome.Failed<UninstallResult>) outcome;
  }

  private static UninstallResult succeeded(CommandOutcome<UninstallResult> outcome) {
    check(
        outcome instanceof CommandOutcome.Succeeded<UninstallResult>,
        "should have succeeded: " + outcome);
    return ((CommandOutcome.Succeeded<UninstallResult>) outcome).result();
  }

  private static InvalidArgumentException refusal(Supplier<UninstallPackages> creation) {
    try {
      creation.get();
      return null;
    } catch (InvalidArgumentException failure) {
      return failure;
    }
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    // one package
    try (var device = deviceWithApplications();
        var session = session(device)) {
      var events = new Events();
      var result = succeeded(session.execute(new UninstallPackages(List.of(FIRST)), events));
      equal(List.of(FIRST), result.removed(), "one package removed");
      equal(
          List.of("start mode=app&size=0", "remove " + FIRST, "finish removal"),
          device.updateRequests,
          "what the device was asked: start, remove, finish");
      check(
          device.updateAgents.stream().allMatch("QNXWebClient/1.0"::equals),
          "with the agent of BlackBerry's clients: " + device.updateAgents);
      equal("xml", result.toJson().get("bodyFormat"), "the last answer is passed on, as JSON");
      equal(
          List.of(0, 100),
          events.progress.stream().map(Progress::percent).toList(),
          "progress: the start and the end");
      check(
          events.progress.stream()
              .allMatch(
                  report -> report.phase() == UpdatePhase.REMOVING && FIRST.equals(report.item())),
          "progress names the phase and the package");
      check(
          events.logs.contains("Removing package 1 of 1"),
          "the log says what is done: " + events.logs);
      check(
          events.logs.contains("Reading the device properties"),
          "after looking at the package list");
    }

    // two packages, in the order asked, and progress by package
    try (var device = deviceWithApplications();
        var session = session(device)) {
      var events = new Events();
      var result =
          succeeded(session.execute(new UninstallPackages(List.of(SECOND, FIRST)), events));
      equal(List.of(SECOND, FIRST), result.removed(), "two packages, in the order asked");
      equal(
          List.of("start mode=app&size=0", "remove " + SECOND, "remove " + FIRST, "finish removal"),
          device.updateRequests,
          "one start, a removal for each, one finish");
      equal(
          List.of(0, 50, 100),
          events.progress.stream().map(Progress::percent).toList(),
          "progress counts the packages");
    }

    // the layout that the research names is accepted too: DeleteRequest to start, DeleteProgress
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.deleteStartElement = "DeleteRequest";
      device.deleteAnswerElement = "DeleteProgress";
      var result =
          succeeded(session.execute(new UninstallPackages(List.of(SECOND, FIRST)), new Events()));
      equal(
          List.of(SECOND, FIRST),
          result.removed(),
          "the layout of the research: both packages are removed");
    }

    // a removal that the device does not call a success is not taken for one
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.deleteAnswerStatus = "Weird";
      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Events()));
      equal(
          "UNEXPECTED_RESPONSE",
          outcome.error().code().wireName(),
          "a removal that is not a success");
      equal("Weird", outcome.error().details().get("status"), "says what the device said");
      equal(FIRST, outcome.error().details().get("packageId"), "and for which package");
      equal(
          List.of(),
          outcome.partialResult().orElseThrow().removed(),
          "nothing is counted as removed");
      equal(
          List.of("start mode=app&size=0", "remove " + FIRST),
          device.updateRequests,
          "and it stops there");
    }

    // the research shows no answer to the end of a removal; whatever comes is kept
    for (var end : List.of("UpdateEnd", "empty", "DeleteProgress", "Surprise")) {
      try (var device = deviceWithApplications();
          var session = session(device)) {
        device.deleteEnd = end;
        var result =
            succeeded(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
        equal(List.of(FIRST), result.removed(), "an end that answers " + end + " is accepted");
        if (end.equals("empty")) {
          equal("text", result.toJson().get("bodyFormat"), "nothing at all is kept as empty text");
          equal("", result.toJson().get("body"), "and is empty");
        }
        if (end.equals("Surprise")) {
          check(
              String.valueOf(result.toJson().get("body")).contains("Surprise"),
              "an unknown answer at the end is kept whole");
        }
      }
    }

    // an end that the device does not call a success is not taken for one
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.deleteEnd = "Weird";
      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Events()));
      equal(
          "UNEXPECTED_RESPONSE", outcome.error().code().wireName(), "an end that is not a success");
      equal("Weird", outcome.error().details().get("status"), "says what the device said");
      equal(
          List.of(FIRST, SECOND),
          outcome.partialResult().orElseThrow().removed(),
          "and tells what had been removed, which the device did before it");
    }

    // the check against the package list: nothing is sent to remove
    try (var device = deviceWithApplications();
        var session = session(device)) {
      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, "ghost")), new Events()));
      equal("INVALID_ARGUMENT", outcome.error().code().wireName(), "an id that is not installed");
      equal("packageIds[1]", outcome.error().details().get("param"), "is named");
      check(
          outcome.error().message().contains("not installed"),
          "and says why: " + outcome.error().message());
      equal(List.of(), device.updateRequests, "and nothing was sent to remove");
    }
    for (var firmware : List.of("os1", "radio1")) {
      try (var device = deviceWithApplications();
          var session = session(device)) {
        var outcome =
            failed(session.execute(new UninstallPackages(List.of(firmware)), new Events()));
        equal(
            "INVALID_ARGUMENT",
            outcome.error().code().wireName(),
            "firmware is refused: " + firmware);
        check(outcome.error().message().contains("not an application"), "and says why");
        equal(List.of(), device.updateRequests, "and nothing was sent: " + firmware);
      }
    }

    // with the check off the device's own answer to an id it will not remove is seen
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.updateUnremovable.add("ghost");
      var events = new Events();
      var outcome = failed(session.execute(new UninstallPackages(List.of("ghost"), false), events));
      equal("COMMAND_FAILED", outcome.error().code().wireName(), "the device refuses an id");
      equal("Cannot uninstall packageid", outcome.error().message(), "in its own words");
      equal(8, outcome.error().details().get("errorIdentifier"), "with its error number");
      equal(
          List.of("start mode=app&size=0", "remove ghost"),
          device.updateRequests,
          "after a start and the removal");
      check(
          !events.logs.contains("Reading the device properties"),
          "and without looking at the package list");
      equal(List.of(), outcome.partialResult().orElseThrow().removed(), "nothing was removed");
    }

    // a device does not check the id: it says Success for a package that is not installed
    try (var device = deviceWithApplications();
        var session = session(device)) {
      var result =
          succeeded(
              session.execute(
                  new UninstallPackages(List.of("not.installed"), false), new Events()));
      equal(
          List.of("not.installed"),
          result.removed(),
          "with the check off, what the device says is all there is: it says yes to any id");
    }

    // the device refuses at each stage, and what was removed is told
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.updateFailsAt = "start";
      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
      equal("unable to verify app manifest", outcome.error().message(), "a refused start");
      equal(List.of("start mode=app&size=0"), device.updateRequests, "stops there");
    }
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.updateUnremovable.add(SECOND);
      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Events()));
      equal("Cannot uninstall packageid", outcome.error().message(), "a refused removal");
      equal(
          List.of(FIRST),
          outcome.partialResult().orElseThrow().removed(),
          "after the first was removed: that is told");
    }
    try (var device = deviceWithApplications();
        var session = session(device)) {
      device.updateFailsAt = "finish";
      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
      equal("Cannot finish", outcome.error().message(), "a refused end");
      equal(
          List.of(FIRST),
          outcome.partialResult().orElseThrow().removed(),
          "tells what had been removed");
    }

    // answers that do not fit
    for (var odd : List.of("<RimTabletResponse><Surprise/></RimTabletResponse>", "hello")) {
      try (var device = deviceWithApplications();
          var session = session(device)) {
        device.updateAnswer = odd;
        var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
        equal(
            "UNEXPECTED_RESPONSE",
            outcome.error().code().wireName(),
            "an answer that does not fit: " + odd);
        check(
            String.valueOf(outcome.partialResult().orElseThrow().toJson().get("body"))
                .contains(odd.equals("hello") ? "hello" : "Surprise"),
            "and is kept whole: " + odd);
      }
    }

    // what is never repeated: the connection drops on the removal
    try (var device = deviceWithApplications();
        var session = session(device)) {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      // the third request of the command is the removal; it is dropped without an answer
      device.afterNextCgiRequest = () -> device.afterNextCgiRequest = () -> device.dropNext = 1;
      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
      equal("CONNECTION_LOST", outcome.error().code().wireName(), "no answer to a removal");
      equal(
          List.of("start mode=app&size=0", "dropped, not answered"),
          device.updateRequests,
          "the removal went out once and was not repeated");
    }

    // a session lost in the middle ends the command
    try (var device = deviceWithApplications();
        var session = session(device)) {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      device.afterNextCgiRequest = () -> device.afterNextCgiRequest = device::expireSessions;
      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Events()));
      equal("AUTH_FAILED", outcome.error().code().wireName(), "a session lost in the middle");
      equal(
          "The device ended the session while the command was running",
          outcome.error().message(),
          "is said plainly");
      equal(
          List.of("start mode=app&size=0", "no session"),
          device.updateRequests,
          "and nothing is repeated");
    }

    // what is refused before anything is sent
    check(
        refusal(() -> new UninstallPackages(List.of())).parameter().equals("packageIds"),
        "no package at all");
    for (var bad : List.of("", "a b", "../x", "a/b", "x".repeat(129))) {
      var failure = refusal(() -> new UninstallPackages(List.of(FIRST, bad)));
      check(
          failure != null && failure.parameter().equals("packageIds[1]"),
          "not a package id: [" + bad + "]");
    }
    var duplicate = refusal(() -> new UninstallPackages(List.of(FIRST, SECOND, FIRST)));
    equal(
        "packageIds[2]",
        duplicate.parameter(),
        "the same package twice is refused, naming the second");
    check(duplicate.getMessage().contains("packageIds[0]"), "and the first");
    check(
        new UninstallPackages(List.of(FIRST)).check(), "the check is on unless it is switched off");
    equal(false, new UninstallPackages(List.of(FIRST)).readOnly(), "removing changes the device");
    equal("app", UpdateMode.UNINSTALL_APPLICATIONS.wireName(), "the mode that removes");
  }
}
