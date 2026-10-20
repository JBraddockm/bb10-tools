package com.jbraddockm.link.update;

import static com.jbraddockm.link.testing.Steps.effects;
import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.partial;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.BodyFormat;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.StepEffect;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link InstallConversation}: the dialogue with the device during an installation. */
@DisplayName("InstallConversation: the installation of packages, one answer at a time")
class InstallConversationTest {

  private static final String FIRST_NAME = "application--first.bar";

  @TempDir private Path folder;
  private BarInfo first;
  private BarInfo second;
  private long firstSize;
  private long secondSize;

  @BeforeEach
  void readTheBars() throws Exception {
    first = BarResources.inspect("application--first.bar", folder);
    second = BarResources.inspect("application--second.bar", folder);
    firstSize = Files.size(first.file());
    secondSize = Files.size(second.file());
  }

  private Dialogue<InstallResult> installing(BarInfo... bars) {
    return new Dialogue<>(new InstallPackages(List.of(bars), Duration.ofSeconds(1)).conversation());
  }

  private Dialogue<InstallResult> installingWithPollEvery(Duration interval, BarInfo... bars) {
    return new Dialogue<>(new InstallPackages(List.of(bars), interval).conversation());
  }

  private Dialogue<InstallResult> afterStart(Dialogue<InstallResult> dialogue, long total) {
    dialogue.start();
    dialogue.answerWithFreeSpace(total * 2);
    dialogue.answer("UpdateStart", "Success");
    return dialogue;
  }

  private static CgiRequest.Form form(Step<InstallResult> step) {
    return (CgiRequest.Form) sent(step);
  }

  @Nested
  @DisplayName("the space")
  class Space {

    @Test
    @DisplayName("is read first, in the properties of the device")
    void isReadFirst() {
      assertThat(sent(installing(first).start())).isEqualTo(GetDeviceProperties.request());
    }

    @Test
    @DisplayName(
        "is enough when it is one and a half times the size of what is sent: the installation is"
            + " started with that size")
    void isEnoughAtOneAndAHalfTimes() {
      var dialogue = installing(first);
      dialogue.start();
      var needed = firstSize + firstSize * 50 / 100;

      var request = form(dialogue.answerWithFreeSpace(needed));

      assertThat(request.endpoint()).isEqualTo(Endpoint.UPDATE);
      assertThat(request.encodedBody()).isEqualTo("mode=bar&size=" + firstSize);
      assertThat(request.describe()).isEqualTo("Starting the installation");
      assertThat(request.readTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName(
        "is not enough when it is one byte less: NOT_ENOUGH_SPACE, with what is needed, what is"
            + " free and what is sent")
    void isNotEnoughOneByteLess() {
      var dialogue = installing(first);
      dialogue.start();
      var needed = firstSize + firstSize * 50 / 100;

      var error = failed(dialogue.answerWithFreeSpace(needed - 1));

      assertThat(error.code()).isEqualTo(LinkErrorCode.NOT_ENOUGH_SPACE);
      assertThat(error.message())
          .isEqualTo("The device does not have enough free space for the installation");
      assertThat(error.details())
          .containsEntry("requiredBytes", needed)
          .containsEntry("freeBytes", needed - 1)
          .containsEntry("packageBytes", firstSize);
    }

    @Test
    @DisplayName("is worked out on the size of all the packages together")
    void isWorkedOutOnAllThePackages() {
      var dialogue = installing(first, second);
      dialogue.start();
      var total = firstSize + secondSize;

      assertThat(form(dialogue.answerWithFreeSpace(total + total * 50 / 100)).encodedBody())
          .isEqualTo("mode=bar&size=" + total);
    }

    @Test
    @DisplayName(
        "that the device does not say is not checked, with a warning, and the installation goes on")
    void aSpaceThatIsNotSaid() {
      var dialogue = installing(first);
      dialogue.start();

      var step = dialogue.answer("update--properties-without-space.xml", Map.of());

      assertThat(form(step).encodedBody()).isEqualTo("mode=bar&size=" + firstSize);
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Note(
                  LogLevel.WARN,
                  "The device does not say how much space is free; the check of the space is"
                      + " skipped"));
    }

    @Test
    @DisplayName("fails with the error that the device reported when it cannot give its properties")
    void anErrorOfTheDevice() {
      var dialogue = installing(first);
      dialogue.start();

      var error = failed(dialogue.answerWithError("DynamicProperties"));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo("Error - something failed");
    }

    @Test
    @DisplayName("fails when the answer is about something else than the properties")
    void anotherElement() {
      var dialogue = installing(first);
      dialogue.start();
      var body = Resources.text(InstallConversationTest.class, "update--wrong-element.xml");

      assertThat(failed(dialogue.answer("update--wrong-element.xml", Map.of())))
          .isEqualTo(
              ResponseFailures.notExpected(
                  ResponseEnvelope.parse(body), ResponseElement.DYNAMIC_PROPERTIES));
    }

    @Test
    @DisplayName("fails with the words for an answer that is not XML, and has installed nothing")
    void notXml() {
      var dialogue = installing(first);
      dialogue.start();

      var step = dialogue.answerWithText("update--not-xml.txt");

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.installed()).isEmpty());
    }
  }

  @Nested
  @DisplayName("the start and the sending")
  class StartAndSending {

    @Test
    @DisplayName(
        "once the device has started, the first package is sent: a POST of the file to update.cgi,"
            + " with type=bar")
    void theFirstPackageIsSent() {
      var dialogue = installing(first);
      dialogue.start();
      dialogue.answerWithFreeSpace(firstSize * 2);

      var step = dialogue.answer("UpdateStart", "Success");

      var upload = (CgiRequest.Upload) sent(step);
      assertThat(upload.pathAndQuery()).isEqualTo("/cgi-bin/update.cgi?type=bar");
      assertThat(upload.file()).isEqualTo(first.file());
      assertThat(upload.length()).isEqualTo(firstSize);
      assertThat(upload.describe()).isEqualTo("Sending package 1 of 1");
      assertThat(upload.readTimeout()).isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    @DisplayName(
        "an error of the device at the start fails the installation, which has installed nothing")
    void anErrorAtTheStart() {
      var dialogue = installing(first);
      dialogue.start();
      dialogue.answerWithFreeSpace(firstSize * 2);

      var step = dialogue.answerWithError("UpdateStart");

      assertThat(failed(step).message()).isEqualTo("Error - something failed");
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.installed()).isEmpty());
    }

    @Test
    @DisplayName("an answer to the start that is about something else is not what was expected")
    void anotherElementAtTheStart() {
      var dialogue = installing(first);
      dialogue.start();
      dialogue.answerWithFreeSpace(firstSize * 2);
      var body = Resources.text(InstallConversationTest.class, "update--wrong-element.xml");

      assertThat(failed(dialogue.answer("update--wrong-element.xml", Map.of())))
          .isEqualTo(
              ResponseFailures.notExpected(
                  ResponseEnvelope.parse(body), ResponseElement.UPDATE_START));
    }

    @Test
    @DisplayName(
        "once the device has received the package it is polled, and 0 percent is reported for the"
            + " package that is installing")
    void theDeviceIsPolled() {
      var dialogue = afterStart(installing(first), firstSize);

      var step = dialogue.answer("UpdateSend", "Success");

      var poll = form(step);
      assertThat(poll.encodedBody()).isEmpty();
      assertThat(poll.describe()).isEqualTo("Waiting for the device to install the package");
      assertThat(poll.readTimeout()).isEqualTo(Duration.ofMinutes(10));
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(
                  new Progress(0, UpdatePhase.INSTALLING, null, null, FIRST_NAME)));
    }

    @Test
    @DisplayName("an error or another element when the package was sent fails the installation")
    void anErrorWhenSent() {
      var dialogue = afterStart(installing(first), firstSize);
      var step = dialogue.answerWithError("UpdateSend");

      assertThat(failed(step).message()).isEqualTo("Error - something failed");
    }

    @Test
    @DisplayName(
        "an answer when the package was sent that is about something else is not what was expected")
    void anotherElementWhenSent() {
      var dialogue = afterStart(installing(first), firstSize);
      var body = Resources.text(InstallConversationTest.class, "update--wrong-element.xml");

      assertThat(failed(dialogue.answer("update--wrong-element.xml", Map.of())))
          .isEqualTo(
              ResponseFailures.notExpected(
                  ResponseEnvelope.parse(body), ResponseElement.UPDATE_SEND));
    }
  }

  @Nested
  @DisplayName("the wait for the installation")
  class Waiting {

    private Dialogue<InstallResult> waiting(BarInfo... bars) {
      var dialogue =
          afterStart(installing(bars), Arrays.stream(bars).mapToLong(BarInfo::sizeBytes).sum());
      dialogue.answer("UpdateSend", "Success");
      return dialogue;
    }

    @ParameterizedTest(name = "{0} with progress {1} is {2} percent")
    @CsvSource({
      "InProgress, 40, 40",
      "Ready, 7, 7",
      "inprogress, 0, 0",
      "InProgress, 100, 100",
      "InProgress, 150, 100",
      "InProgress, -5, 0",
      "InProgress, many, 0",
      "InProgress, ' 70 ', 70"
    })
    @DisplayName(
        "a progress that is in progress or ready is polled again after a pause, with the percent"
            + " that it says, kept between 0 and 100")
    void aProgressThatIsNotOver(String status, String progress, int percent) {
      var step = waiting(first).answer("UpdateProgress", status, progress);

      assertThat(form(step).encodedBody()).isEmpty();
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(
                  new Progress(percent, UpdatePhase.INSTALLING, null, null, FIRST_NAME)),
              new StepEffect.Pause(Duration.ofSeconds(1)));
    }

    @Test
    @DisplayName("a progress with no percent is 0 percent")
    void noPercent() {
      var step = waiting(first).answer("UpdateProgress", "InProgress");

      assertThat(effects(step).getFirst())
          .isEqualTo(
              new StepEffect.Report(
                  new Progress(0, UpdatePhase.INSTALLING, null, null, FIRST_NAME)));
    }

    @Test
    @DisplayName(
        "an answer that the package was received, again, is a wait of 0 percent, as the device"
            + " repeats itself")
    void theSendAnswerAgain() {
      var step = waiting(first).answer("UpdateSend", "Success");

      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(
                  new Progress(0, UpdatePhase.INSTALLING, null, null, FIRST_NAME)),
              new StepEffect.Pause(Duration.ofSeconds(1)));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Success", "Complete", "success"})
    @DisplayName(
        "a success of the last package asks the device to finish the installation, reporting 100"
            + " percent")
    void aSuccessOfTheLastPackage(String status) {
      var step = waiting(first).answer("UpdateProgress", status);

      var finish = form(step);
      assertThat(finish.encodedBody()).isEqualTo("status=success");
      assertThat(finish.describe()).isEqualTo("Finishing the installation");
      assertThat(finish.readTimeout()).isEqualTo(Duration.ofMinutes(10));
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(
                  new Progress(100, UpdatePhase.INSTALLING, null, null, FIRST_NAME)));
    }

    @Test
    @DisplayName("an error status fails the installation, with the words for it")
    void anErrorStatus() {
      var step = waiting(first).answer("UpdateProgress", "Error");

      assertThat(failed(step).code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failed(step).message()).isEqualTo("The device reported an error");
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"Strange", "In Progress", ""})
    @DisplayName("a status that is not known fails the installation, with the status that was said")
    void anUnknownStatus(String status) {
      var step = waiting(first).answer("UpdateProgress", status);

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failed(step).message())
          .isEqualTo("The device reported a progress status that this version does not know");
      assertThat(failed(step).details()).containsEntry("status", status);
    }

    @Test
    @DisplayName(
        "a progress with no status is a status that is not known, and says so as the empty status")
    void noStatus() {
      var step = waiting(first).answerWithoutStatus("UpdateProgress");

      assertThat(failed(step).details()).containsEntry("status", "");
    }

    @Test
    @DisplayName("an error of the device while waiting fails the installation with that error")
    void anErrorWhileWaiting() {
      assertThat(failed(waiting(first).answerWithError("UpdateProgress")).message())
          .isEqualTo("Error - something failed");
    }

    @Test
    @DisplayName(
        "an answer about something else while waiting is not what was expected: a progress")
    void anotherElementWhileWaiting() {
      var body = Resources.text(InstallConversationTest.class, "update--wrong-element.xml");

      assertThat(failed(waiting(first).answer("update--wrong-element.xml", Map.of())))
          .isEqualTo(
              ResponseFailures.notExpected(
                  ResponseEnvelope.parse(body), ResponseElement.UPDATE_PROGRESS));
    }

    @Test
    @DisplayName(
        "an end that comes while waiting for the last package finishes the installation at once,"
            + " with the package installed")
    void anEndWhileWaiting() {
      var step = waiting(first).answer("UpdateEnd", "Success");

      assertThat(finished(step).installed())
          .extracting(InstalledPackage::packageId)
          .containsExactly("idFirst");
    }

    @Test
    @DisplayName(
        "an end that comes while there are packages left to send fails the installation, which has"
            + " installed the first")
    void anEndTooSoon() {
      var step = waiting(first, second).answer("UpdateEnd", "Success");

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failed(step).message())
          .isEqualTo("The device ended the installation before all packages were sent");
      assertThat(partial(step))
          .hasValueSatisfying(
              result ->
                  assertThat(result.installed())
                      .extracting(InstalledPackage::packageId)
                      .containsExactly("idFirst"));
    }

    @Test
    @DisplayName(
        "is given up after 30 minutes of polling: 3 polls at an interval of 10 minutes, and the 4th"
            + " is too many")
    void isGivenUp() {
      var dialogue = afterStart(installingWithPollEvery(Duration.ofMinutes(10), first), firstSize);
      dialogue.answer("UpdateSend", "Success");

      for (var poll = 1; poll <= 3; poll++) {
        assertThat(sent(dialogue.answer("UpdateProgress", "InProgress", "10")))
            .isInstanceOf(CgiRequest.Form.class);
      }
      var step = dialogue.answer("UpdateProgress", "InProgress", "10");

      assertThat(failed(step).code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failed(step).message())
          .isEqualTo("The device did not finish installing the package in time");
      assertThat(failed(step).details()).containsEntry("waitedMinutes", 30L);
    }

    @Test
    @DisplayName("is given a fresh count of polls for each package")
    void aFreshCountForEachPackage() {
      var dialogue =
          afterStart(
              installingWithPollEvery(Duration.ofMinutes(10), first, second),
              firstSize + secondSize);
      dialogue.answer("UpdateSend", "Success");
      dialogue.answer("UpdateProgress", "InProgress", "10");
      dialogue.answer("UpdateProgress", "InProgress", "20");
      dialogue.answer("UpdateProgress", "InProgress", "30");
      dialogue.answer("UpdateProgress", "Success");
      dialogue.answer("UpdateSend", "Success");

      for (var poll = 1; poll <= 3; poll++) {
        assertThat(sent(dialogue.answer("UpdateProgress", "InProgress", "10")))
            .isInstanceOf(CgiRequest.Form.class);
      }
    }
  }

  @Nested
  @DisplayName("more than one package")
  class SeveralPackages {

    @Test
    @DisplayName("sends the next package when the first is installed, reporting 100 percent")
    void sendsTheNextPackage() {
      var dialogue = afterStart(installing(first, second), firstSize + secondSize);
      dialogue.answer("UpdateSend", "Success");

      var step = dialogue.answer("UpdateProgress", "Success");

      var upload = (CgiRequest.Upload) sent(step);
      assertThat(upload.file()).isEqualTo(second.file());
      assertThat(upload.describe()).isEqualTo("Sending package 2 of 2");
      assertThat(effects(step))
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              StepEffect.Report.class,
              report -> {
                assertThat(report.progress().percent()).isEqualTo(100);
                assertThat(report.progress().phase()).isEqualTo(UpdatePhase.INSTALLING);
              });
    }

    @Test
    @DisplayName(
        "finishes with all the packages that were installed, in order, and the last answer of the"
            + " device, when the end is a success")
    void finishesWithAllThePackages() {
      var dialogue = afterStart(installing(first, second), firstSize + secondSize);
      dialogue.answer("UpdateSend", "Success");
      dialogue.answer("UpdateProgress", "Success");
      dialogue.answer("UpdateSend", "Success");
      dialogue.answer("UpdateProgress", "Complete");

      var result = finished(dialogue.answer("UpdateEnd", "Success"));

      assertThat(result.installed())
          .extracting(InstalledPackage::packageId)
          .containsExactly("idFirst", "idSecond");
      assertThat(result.answer().format()).isEqualTo(BodyFormat.XML);
    }

    @Test
    @DisplayName("a failure with the second package keeps the first as installed")
    void aFailureKeepsWhatWasInstalled() {
      var dialogue = afterStart(installing(first, second), firstSize + secondSize);
      dialogue.answer("UpdateSend", "Success");
      dialogue.answer("UpdateProgress", "Success");
      dialogue.answer("UpdateSend", "Success");

      var step = dialogue.answer("UpdateProgress", "Error");

      assertThat(partial(step))
          .hasValueSatisfying(
              result ->
                  assertThat(result.installed())
                      .extracting(InstalledPackage::packageId)
                      .containsExactly("idFirst"));
    }
  }

  @Nested
  @DisplayName("the end")
  class End {

    private Dialogue<InstallResult> finishing() {
      var dialogue = afterStart(installing(first), firstSize);
      dialogue.answer("UpdateSend", "Success");
      dialogue.answer("UpdateProgress", "Success");
      return dialogue;
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Success", "Complete", "success"})
    @DisplayName("a success ends the installation, with the packages and the answer of the device")
    void aSuccess(String status) {
      var result = finished(finishing().answer("UpdateEnd", status));

      assertThat(result.installed())
          .extracting(InstalledPackage::packageId)
          .containsExactly("idFirst");
    }

    @Test
    @DisplayName("an end with no status is accepted as a success")
    void noStatusIsASuccess() {
      assertThat(finished(finishing().answerWithoutStatus("UpdateEnd")).installed()).hasSize(1);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"InProgress", "Ready", "Strange"})
    @DisplayName(
        "an end with another status is not taken for a success, and the status is said, with the"
            + " package that was installed kept")
    void anotherStatusIsNotASuccess(String status) {
      var step = finishing().answer("UpdateEnd", status);

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failed(step).message())
          .isEqualTo("The device ended the installation with a status that is not a success");
      assertThat(failed(step).details()).containsEntry("status", status);
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.installed()).hasSize(1));
    }

    @Test
    @DisplayName(
        "an end with the status Error is an error of the device, and fails the installation as"
            + " such")
    void anErrorStatusAtTheEnd() {
      var step = finishing().answer("UpdateEnd", "Error");

      assertThat(failed(step).code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.installed()).hasSize(1));
    }

    @Test
    @DisplayName("an error of the device at the end fails the installation with that error")
    void anErrorAtTheEnd() {
      assertThat(failed(finishing().answerWithError("UpdateEnd")).message())
          .isEqualTo("Error - something failed");
    }

    @Test
    @DisplayName("an answer at the end that is about something else is not what was expected")
    void anotherElementAtTheEnd() {
      var body = Resources.text(InstallConversationTest.class, "update--wrong-element.xml");

      assertThat(failed(finishing().answer("update--wrong-element.xml", Map.of())))
          .isEqualTo(
              ResponseFailures.notExpected(
                  ResponseEnvelope.parse(body), ResponseElement.UPDATE_END));
    }

    @Test
    @DisplayName(
        "an answer that is not XML fails the installation at its end, with the words for that, and"
            + " keeps the package that was installed")
    void notXmlAtTheEnd() {
      var step = finishing().answerWithText("update--not-xml.txt");

      var answer =
          new CgiResponse(
              200,
              "text/html",
              Resources.text(InstallConversationTest.class, "update--not-xml.txt"),
              null);
      assertThat(failed(step)).isEqualTo(ResponseFailures.notXml(answer));
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.installed()).hasSize(1));
    }
  }
}
