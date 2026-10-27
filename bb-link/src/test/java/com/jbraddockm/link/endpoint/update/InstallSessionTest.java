package com.jbraddockm.link.endpoint.update;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.net.InetAddress;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for installation through a session against the fake device, with the bars sent over HTTP.
 */
@DisplayName("Installing through a session: what is asked of the device, and what is told")
class InstallSessionTest {

  private static final Duration QUICK = Duration.ofMillis(5);

  @TempDir private Path folder;
  private FakeDevice device;
  private DeviceSession session;
  private BarInfo first;
  private BarInfo second;
  private final List<String> trace = Collections.synchronizedList(new ArrayList<>());

  @BeforeEach
  void startTheDevice() throws Exception {
    device = new FakeDevice("secret");
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                InetAddress.getLoopbackAddress().getHostAddress(),
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")),
            (line, body, _, _) -> trace.add(line + " | " + body));
    first = BarResources.inspect("application--first.bar", folder);
    second = BarResources.inspect("application--second.bar", folder);
  }

  @AfterEach
  void closeEverything() {
    session.close();
    device.close();
  }

  private InstallPackages install(BarInfo... bars) {
    return new InstallPackages(List.of(bars), QUICK);
  }

  private static List<String> lines(String resource, long size) {
    return Resources.text(InstallSessionTest.class, resource, Map.of("size", String.valueOf(size)))
        .lines()
        .toList();
  }

  @Nested
  @DisplayName("a whole installation")
  class Whole {

    @Test
    @DisplayName(
        "of one package: the device is asked to start, to take the file, three polls and to finish,"
            + " and the package is the one that was sent")
    void onePackage() {
      var logs = new Logs();

      var result = succeeded(session.execute(install(first), logs));

      assertThat(result.installed())
          .extracting(InstalledPackage::packageId)
          .containsExactly("idFirst");
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--install-requests.txt", first.sizeBytes()));
      assertThat(device.updateAgents).allMatch("QNXWebClient/1.0"::equals);
      assertThat(result.toJson()).containsEntry("bodyFormat", "xml");
    }

    @Test
    @DisplayName(
        "tells the progress: the start, the polls that were not done and the end, each naming the"
            + " bar, and a poll is logged once")
    void tellsTheProgress() {
      var logs = new Logs();
      var expected =
          Resources.text(InstallSessionTest.class, "session--install-progress.txt")
              .lines()
              .map(Integer::parseInt)
              .toList();

      succeeded(session.execute(install(first), logs));

      assertThat(logs.progress()).extracting(Progress::percent).containsExactlyElementsOf(expected);
      assertThat(logs.progress())
          .extracting(Progress::item)
          .allMatch(first.file().getFileName().toString()::equals);
      assertThat(
              logs.messages(LogLevel.INFO).stream()
                  .filter("Waiting for the device to install the package"::equals))
          .hasSize(1);
    }

    @Test
    @DisplayName(
        "of two packages: in order, the device is told the sum of the sizes, takes two files, and"
            + " finishes once, at the end")
    void twoPackages() {
      var result = succeeded(session.execute(install(first, second), new Logs()));

      assertThat(result.installed())
          .extracting(InstalledPackage::packageId)
          .containsExactly("idFirst", "idSecond");
      assertThat(device.updateRequests.getFirst())
          .isEqualTo(
              lines("session--install-start-only.txt", first.sizeBytes() + second.sizeBytes())
                  .getFirst());
      assertThat(device.updateRequests.stream().filter(line -> line.startsWith("upload")))
          .hasSize(2);
      assertThat(device.updateRequests.getLast()).isEqualTo("finish");
    }

    @Test
    @DisplayName(
        "of a larger file: the upload is counted as it goes, each report with the total and the"
            + " name, never going backwards and ending near the end; the trace shows the size and"
            + " not the content")
    void aLargerFile() throws Exception {
      var larger = BarResources.inspect("application--larger.bar", folder);
      device.updateProgress = List.of(100);
      var logs = new Logs();

      succeeded(session.execute(install(larger), logs));

      var uploads =
          logs.progress().stream()
              .filter(report -> report.phase() == UpdatePhase.UPLOADING)
              .toList();
      assertThat(uploads.size()).isGreaterThanOrEqualTo(2);
      assertThat(uploads)
          .allMatch(
              report ->
                  report.bytesTotal() == larger.sizeBytes()
                      && larger.file().getFileName().toString().equals(report.item()));
      assertThat(uploads.stream().map(Progress::percent).toList()).isSorted();
      assertThat(uploads.getLast().percent()).isGreaterThan(80);
      assertThat(trace)
          .anyMatch(
              entry ->
                  entry.contains(
                      "(application/octet-stream, "
                          + larger.sizeBytes()
                          + " bytes: application--larger.bar)"));
      assertThat(trace).allMatch(entry -> entry.length() < 400);
    }

    @Test
    @DisplayName(
        "does not depend on the cookie that the device sets, which arrives already over and is"
            + " never sent back")
    void noCookieIsSentBack() {
      succeeded(session.execute(install(first), new Logs()));

      assertThat(device.cookiesSeen()).noneMatch(cookie -> cookie.contains("update="));
    }
  }

  @Nested
  @DisplayName("a device that refuses")
  class Refusing {

    @ParameterizedTest(name = "at the {0}")
    @ValueSource(strings = {"start", "send", "progress"})
    @DisplayName(
        "fails with COMMAND_FAILED and its error number, keeps the answer, and has installed"
            + " nothing")
    void refusesAtEachStage(String stage) {
      device.updateFailsAt = stage;

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(outcome.error().details().get("errorIdentifier")).isInstanceOf(Integer.class);
      assertThat(outcome.partialResult())
          .hasValueSatisfying(partial -> assertThat(partial.installed()).isEmpty());
    }

    @Test
    @DisplayName("at the start says so in its own words, and nothing is uploaded")
    void refusesAtTheStart() {
      device.updateFailsAt = "start";

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().message()).isEqualTo("unable to verify app manifest");
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--install-start-only.txt", first.sizeBytes()));
    }

    @Test
    @DisplayName("at the very end tells that the package was installed")
    void refusesAtTheEnd() {
      device.updateFailsAt = "finish";

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().message()).isEqualTo("Cannot finish");
      assertThat(outcome.partialResult())
          .hasValueSatisfying(partial -> assertThat(partial.installed()).hasSize(1));
    }
  }

  @Nested
  @DisplayName("answers that do not fit")
  class OddAnswers {

    @ParameterizedTest(name = "{0} is kept whole")
    @ValueSource(strings = {"session--surprise.xml", "session--hello.txt"})
    @DisplayName(
        "an answer that is not what was asked is UNEXPECTED_RESPONSE, and is kept whole in the"
            + " partial result")
    void anAnswerThatDoesNotFit(String resource) {
      var odd = Resources.text(InstallSessionTest.class, resource);
      device.updateAnswer = odd;

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(String.valueOf(outcome.partialResult().orElseThrow().toJson().get("body")))
          .contains(resource.contains("surprise") ? "Surprise" : "hello");
    }

    @Test
    @DisplayName("a status that is not known is UNEXPECTED_RESPONSE, and is named")
    void aStatusThatIsNotKnown() {
      device.updatePollAnswer =
          Resources.text(InstallSessionTest.class, "session--poll-sideways.xml");

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(outcome.error().details()).containsEntry("status", "Sideways");
    }

    @Test
    @DisplayName("Complete ends a package like Success")
    void completeIsSuccess() {
      device.updatePollAnswer =
          Resources.text(InstallSessionTest.class, "session--poll-complete.xml");

      assertThat(succeeded(session.execute(install(first), new Logs())).installed()).hasSize(1);
    }

    @ParameterizedTest(name = "an end with the status [{0}]")
    @ValueSource(strings = {"Success", "Complete", ""})
    @DisplayName(
        "an end with the status of a success, or with none, is a success, as a real device ends it")
    void anEndThatIsASuccess(String status) {
      device.updateEndStatus = status;

      assertThat(succeeded(session.execute(install(first), new Logs())).installed()).hasSize(1);
    }

    @Test
    @DisplayName("an end with another status names it, and tells what was installed")
    void anEndThatIsNotASuccess() {
      device.updateEndStatus = "Weird";

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(outcome.error().details()).containsEntry("status", "Weird");
      assertThat(outcome.partialResult())
          .hasValueSatisfying(partial -> assertThat(partial.installed()).hasSize(1));
    }
  }

  @Nested
  @DisplayName("the space")
  class Space {

    @Test
    @DisplayName(
        "that is too little is NOT_ENOUGH_SPACE, with one and a half times the size against what is"
            + " free, and nothing is sent")
    void tooLittle() {
      device.propertiesXml =
          Resources.text(
              InstallSessionTest.class,
              "update--properties.xml",
              Map.of("free", String.valueOf(first.sizeBytes())));

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(LinkErrorCode.NOT_ENOUGH_SPACE);
      assertThat(outcome.error().details())
          .containsEntry("requiredBytes", first.sizeBytes() * 3 / 2)
          .containsEntry("freeBytes", first.sizeBytes());
      assertThat(device.updateRequests).isEmpty();
    }

    @Test
    @DisplayName("that is exactly enough is enough")
    void exactlyEnough() {
      device.propertiesXml =
          Resources.text(
              InstallSessionTest.class,
              "update--properties.xml",
              Map.of("free", String.valueOf(first.sizeBytes() + first.sizeBytes() / 2)));

      assertThat(succeeded(session.execute(install(first), new Logs())).installed()).hasSize(1);
    }

    @Test
    @DisplayName("that the device does not say is told in a warning, and the installation goes on")
    void notSaid() {
      var logs = new Logs();

      succeeded(session.execute(install(first), logs));

      assertThat(logs.messages(LogLevel.WARN))
          .anyMatch(text -> text.contains("does not say how much space is free"));
    }
  }

  @Nested
  @DisplayName("what is never sent twice")
  class NeverRepeated {

    @Test
    @DisplayName(
        "an upload that is cut off is CONNECTION_LOST, and is not sent again: the start and the one"
            + " upload, nothing more")
    void aCutUpload() throws Exception {
      var cut = BarResources.inspect("application--larger.bar", folder);
      device.updateDropUploadAfter = 1000;

      var outcome = failed(session.execute(install(cut), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--install-cut-upload.txt", cut.sizeBytes()));
    }

    @Test
    @DisplayName(
        "a whole file that the device does not answer is CONNECTION_LOST too: it may have installed"
            + " it, so it is not sent again")
    void noAnswerToAWholeUpload() {
      device.updateDropAfterUpload = true;

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.CONNECTION_LOST);
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(
              lines("session--install-upload-unanswered.txt", first.sizeBytes()));
    }
  }

  @Nested
  @DisplayName("the session")
  class TheSession {

    @Test
    @DisplayName(
        "that the device forgot before anything was sent is renewed once, with one more login, and"
            + " the installation goes through")
    void aForgottenSessionIsRenewed() {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      assertThat(device.loginRequests.get()).isEqualTo(2);
      device.expireSessions();

      assertThat(succeeded(session.execute(install(first), new Logs())).installed()).hasSize(1);

      assertThat(device.loginRequests.get()).isEqualTo(4);
    }

    @Test
    @DisplayName(
        "that is lost in the middle ends the command, plainly, and the upload is not sent again")
    void aSessionLostInTheMiddle() {
      session.execute(new GetDeviceProperties(), CommandListener.SILENT);
      // the third request of the command is the upload; the session is lost as it is answered
      device.afterNextCgiRequest =
          () ->
              device.afterNextCgiRequest =
                  () -> device.afterNextCgiRequest = device::expireSessions;

      var outcome = failed(session.execute(install(first), new Logs()));

      assertThat(outcome.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
      assertThat(outcome.error().message())
          .isEqualTo("The device ended the session while the command was running");
      assertThat(device.updateRequests)
          .containsExactlyElementsOf(lines("session--install-session-lost.txt", first.sizeBytes()));
    }
  }
}
