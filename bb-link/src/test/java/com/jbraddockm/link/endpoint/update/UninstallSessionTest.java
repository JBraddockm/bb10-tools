package com.jbraddockm.link.endpoint.update;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for removal through a session against the fake device. */
@DisplayName("Removing through a session: what is asked of the device, and what is told")
class UninstallSessionTest {

  private static final String FIRST = "idFirst";
  private static final String SECOND = "idSecond";

  private FakeDevice device;
  private DeviceSession session;

  @BeforeEach
  void startTheDevice() throws Exception {
    device = new FakeDevice("secret");
    device.propertiesXml =
        Resources.text(UninstallSessionTest.class, "session--uninstall-properties.xml");
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                InetAddress.getLoopbackAddress().getHostAddress(),
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")),
            ExchangeObserver.NONE);
  }

  @AfterEach
  void closeEverything() {
    session.close();
    device.close();
  }

  private static List<String> lines(String resource) {
    return Resources.text(UninstallSessionTest.class, resource).lines().toList();
  }

  private UninstallResult remove(Logs logs, String... ids) {
    return succeeded(session.execute(new UninstallPackages(List.of(ids)), logs));
  }

  @Nested
  @DisplayName("a whole removal")
  class Whole {

    @Test
    @DisplayName(
        "of one package: the device is asked to start, to remove it and to finish, with the agent"
            + " of BlackBerry's clients, and the package is the one that was named")
    void onePackage() {
      var result = remove(new Logs(), FIRST);

      assertThat(result.removed()).containsExactly(FIRST);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-one.txt"));
      assertThat(device.updateAgents).allMatch("QNXWebClient/1.0"::equals);
      assertThat(result.toJson()).containsEntry("bodyFormat", "xml");
    }

    @Test
    @DisplayName(
        "tells the progress, naming the phase and the package, and says what it does, after having"
            + " looked at the packages of the device")
    void tellsTheProgress() {
      var logs = new Logs();

      remove(logs, FIRST);

      assertThat(logs.progress()).extracting(Progress::percent).containsExactly(0, 100);
      assertThat(logs.progress())
          .allMatch(
              report -> report.phase() == UpdatePhase.REMOVING && FIRST.equals(report.item()));
      assertThat(logs.messages(LogLevel.INFO))
          .contains("Reading the device properties", "Removing package 1 of 1");
    }

    @Test
    @DisplayName(
        "of two packages: in the order that they were asked, one start, a removal for each, one"
            + " finish, and the progress counts the packages")
    void twoPackages() {
      var logs = new Logs();

      var result = remove(logs, SECOND, FIRST);

      assertThat(result.removed()).containsExactly(SECOND, FIRST);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-two.txt"));
      assertThat(logs.progress()).extracting(Progress::percent).containsExactly(0, 50, 100);
    }

    @Test
    @DisplayName(
        "is also accepted when the device answers the start with DeleteRequest and every removal"
            + " with DeleteProgress")
    void theOtherLayout() {
      device.deleteStartElement = "DeleteRequest";
      device.deleteAnswerElement = "DeleteProgress";

      assertThat(remove(new Logs(), SECOND, FIRST).removed()).containsExactly(SECOND, FIRST);
    }
  }

  @Nested
  @DisplayName("the end of a removal")
  class End {

    @ParameterizedTest(name = "an end that answers {0}")
    @ValueSource(strings = {"UpdateEnd", "empty", "DeleteProgress", "Surprise"})
    @DisplayName(
        "is accepted, whatever the device answers, since no answer to the end is known: what comes"
            + " is kept")
    void anyEndIsAccepted(String end) {
      device.deleteEnd = end;

      var result = remove(new Logs(), FIRST);

      assertThat(result.removed()).containsExactly(FIRST);
      if (end.equals("empty")) {
        assertThat(result.toJson()).containsEntry("bodyFormat", "text").containsEntry("body", "");
      }
      if (end.equals("Surprise")) {
        assertThat(String.valueOf(result.toJson().get("body"))).contains("Surprise");
      }
    }

    @Test
    @DisplayName(
        "with a status that is not a success is not taken for one: UNEXPECTED_RESPONSE, naming the"
            + " status, and what had been removed is told")
    void aStatusThatIsNotASuccess() {
      device.deleteEnd = "Weird";

      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(outcome.error().details()).containsEntry("status", "Weird");
      assertThat(outcome.partialResult().orElseThrow().removed()).containsExactly(FIRST, SECOND);
    }

    @Test
    @DisplayName(
        "a removal with a status that is not a success is not counted: nothing is removed, it names"
            + " the status and the package, and it stops there")
    void aRemovalThatIsNotASuccess() {
      device.deleteAnswerStatus = "Weird";

      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(outcome.error().details())
          .containsEntry("status", "Weird")
          .containsEntry("packageId", FIRST);
      assertThat(outcome.partialResult().orElseThrow().removed()).isEmpty();
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-stops-at-first.txt"));
    }
  }

  @Nested
  @DisplayName("the check against the packages of the device")
  class Check {

    @Test
    @DisplayName(
        "refuses an id that is not installed, naming its position and saying why, before anything"
            + " is sent to remove")
    void refusesAnIdThatIsNotInstalled() {
      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, "ghost")), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(outcome.error().details()).containsEntry("param", "packageIds[1]");
      assertThat(outcome.error().message()).contains("not installed");
      assertThat(device.updateRequests).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"idOs", "idRadio"})
    @DisplayName(
        "refuses firmware, the system and the radio alike, saying that it is not an application,"
            + " and sends nothing")
    void refusesFirmware(String firmware) {
      var outcome = failed(session.execute(new UninstallPackages(List.of(firmware)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(outcome.error().message()).contains("not an application");
      assertThat(device.updateRequests).isEmpty();
    }

    @Test
    @DisplayName(
        "when it is off, shows what the device says to an id that it will not remove: its own words"
            + " and its error number, without a look at the list")
    void withTheCheckOff() {
      device.updateUnremovable.add("ghost");
      var logs = new Logs();

      var outcome = failed(session.execute(new UninstallPackages(List.of("ghost"), false), logs));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(outcome.error().message()).isEqualTo("Cannot uninstall packageid");
      assertThat(outcome.error().details()).containsEntry("errorIdentifier", 8);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-ghost.txt"));
      assertThat(logs.messages(LogLevel.INFO)).doesNotContain("Reading the device properties");
      assertThat(outcome.partialResult().orElseThrow().removed()).isEmpty();
    }

    @Test
    @DisplayName(
        "when it is off, is not a check of the id at all: a device that says yes to any id has"
            + " removed it")
    void aDeviceThatSaysYesToAnyId() {
      var result =
          succeeded(
              session.execute(new UninstallPackages(List.of("not.installed"), false), new Logs()));

      assertThat(result.removed()).containsExactly("not.installed");
    }
  }

  @Nested
  @DisplayName("a device that refuses")
  class Refusing {

    @Test
    @DisplayName("at the start says so in its own words, and the removal stops there")
    void atTheStart() {
      device.updateFailsAt = "start";

      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Logs()));

      assertThat(outcome.error().message()).isEqualTo("unable to verify app manifest");
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-start-only.txt"));
    }

    @Test
    @DisplayName("at a removal tells what had been removed before it")
    void atASecondRemoval() {
      device.updateUnremovable.add(SECOND);

      var outcome =
          failed(session.execute(new UninstallPackages(List.of(FIRST, SECOND)), new Logs()));

      assertThat(outcome.error().message()).isEqualTo("Cannot uninstall packageid");
      assertThat(outcome.partialResult().orElseThrow().removed()).containsExactly(FIRST);
    }

    @Test
    @DisplayName("at the end tells what had been removed")
    void atTheEnd() {
      device.updateFailsAt = "finish";

      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Logs()));

      assertThat(outcome.error().message()).isEqualTo("Cannot finish");
      assertThat(outcome.partialResult().orElseThrow().removed()).containsExactly(FIRST);
    }

    @ParameterizedTest(name = "{0} is kept whole")
    @ValueSource(strings = {"session--surprise.xml", "session--hello.txt"})
    @DisplayName(
        "with an answer that is not what was asked is UNEXPECTED_RESPONSE, and the answer is kept"
            + " whole")
    void anAnswerThatDoesNotFit(String resource) {
      device.updateAnswer = Resources.text(UninstallSessionTest.class, resource);

      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(String.valueOf(outcome.partialResult().orElseThrow().toJson().get("body")))
          .contains(resource.contains("surprise") ? "Surprise" : "hello");
    }
  }

  @Nested
  @DisplayName("what is never repeated")
  class NeverRepeated {

    @Test
    @DisplayName(
        "a removal that the device does not answer is CONNECTION_LOST, and is not sent again")
    void aDroppedRemoval() {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      // the third request of the command is the removal; it is dropped without an answer
      device.afterNextCgiRequest = () -> device.afterNextCgiRequest = () -> device.dropNext.set(1);

      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-dropped.txt"));
    }

    @Test
    @DisplayName("a session lost in the middle ends the command, plainly, and nothing is repeated")
    void aSessionLostInTheMiddle() {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      device.afterNextCgiRequest = () -> device.afterNextCgiRequest = device::expireSessions;

      var outcome = failed(session.execute(new UninstallPackages(List.of(FIRST)), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(outcome.error().message())
          .isEqualTo("The device ended the session while the command was running");
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--uninstall-session-lost.txt"));
    }
  }
}
