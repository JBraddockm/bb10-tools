package com.jbraddockm.link.update;

import static com.jbraddockm.link.testing.Steps.effects;
import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.partial;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.StepEffect;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.Resources;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link UninstallConversation}: the dialogue with the device during a removal. */
@DisplayName("UninstallConversation: the removal of packages, one answer at a time")
class UninstallConversationTest {

  private static Dialogue<UninstallResult> checking(String... ids) {
    return new Dialogue<>(new UninstallPackages(List.of(ids), true).conversation());
  }

  private static Dialogue<UninstallResult> unchecked(String... ids) {
    return new Dialogue<>(new UninstallPackages(List.of(ids), false).conversation());
  }

  private static CgiRequest.Form form(Step<UninstallResult> step) {
    return (CgiRequest.Form) sent(step);
  }

  private static Step<UninstallResult> notExpected(
      String resource, ResponseElement expected, Dialogue<UninstallResult> dialogue) {
    var body = Resources.text(UninstallConversationTest.class, resource);
    var step = dialogue.answer(resource, Map.of());
    assertThat(failed(step))
        .isEqualTo(ResponseFailures.notExpected(ResponseEnvelope.parse(body), expected));
    return step;
  }

  private static Dialogue<UninstallResult> removing(String... ids) {
    var dialogue = unchecked(ids);
    dialogue.start();
    dialogue.answer("UpdateStart", "Success");
    return dialogue;
  }

  @Nested
  @DisplayName("the check of the packages")
  class Check {

    @Test
    @DisplayName("is the properties of the device, read first")
    void isReadFirst() {
      assertThat(sent(checking("idFirst").start())).isEqualTo(GetDeviceProperties.request());
    }

    @Test
    @DisplayName(
        "lets the removal start when the device has the applications: mode=app with a size of 0")
    void letsTheRemovalStart() {
      var dialogue = checking("idFirst", "idOther");
      dialogue.start();

      var request = form(dialogue.answer("update--properties-with-packages.xml", Map.of()));

      assertThat(request.encodedBody()).isEqualTo("mode=app&size=0");
      assertThat(request.describe()).isEqualTo("Starting the removal");
      assertThat(request.readTimeout()).isEqualTo(Duration.ofMinutes(2));
    }

    @Test
    @DisplayName("refuses a package that is not on the device, naming its position")
    void refusesAPackageThatIsNotInstalled() {
      var dialogue = checking("idFirst", "idMissing");
      dialogue.start();

      var error = failed(dialogue.answer("update--properties-with-packages.xml", Map.of()));

      assertThat(error.code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(error.message()).contains("is not installed on the device");
      assertThat(error.details()).containsEntry("param", "packageIds[1]");
    }

    @Test
    @DisplayName(
        "refuses a package that is not an application, since removing firmware is not supported")
    void refusesFirmware() {
      var dialogue = checking("idSystem");
      dialogue.start();

      var error = failed(dialogue.answer("update--properties-with-packages.xml", Map.of()));

      assertThat(error.code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(error.message())
          .contains("is not an application; removing firmware is not supported");
      assertThat(error.details()).containsEntry("param", "packageIds[0]");
    }

    @Test
    @DisplayName(
        "refuses at the first package that is wrong, whatever is wrong with the ones after it")
    void refusesAtTheFirstWrongPackage() {
      var dialogue = checking("idMissing", "idSystem");
      dialogue.start();

      assertThat(
              failed(dialogue.answer("update--properties-with-packages.xml", Map.of())).details())
          .containsEntry("param", "packageIds[0]");
    }

    @Test
    @DisplayName(
        "fails with the error of the device when the properties cannot be read, and for an answer"
            + " about something else")
    void failsWhenThePropertiesCannotBeRead() {
      var withError = checking("idFirst");
      withError.start();
      assertThat(failed(withError.answerWithError("DynamicProperties")).message())
          .isEqualTo("Error - something failed");

      var other = checking("idFirst");
      other.start();
      notExpected("update--wrong-element.xml", ResponseElement.DYNAMIC_PROPERTIES, other);
    }

    @Test
    @DisplayName("is not made when it is switched off: the removal is started at once")
    void isNotMadeWhenSwitchedOff() {
      assertThat(form(unchecked("idFirst").start()).encodedBody()).isEqualTo("mode=app&size=0");
    }
  }

  @Nested
  @DisplayName("the start")
  class Start {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"UpdateStart", "DeleteRequest"})
    @DisplayName(
        "once the device has started, the first package is asked to be removed, and 0 percent is"
            + " reported for it")
    void theFirstPackageIsRemoved(String element) {
      var dialogue = unchecked("idFirst", "idOther");
      dialogue.start();

      var step = dialogue.answer(element, "Success");

      var request = form(step);
      assertThat(request.encodedBody()).isEqualTo("type=bar&packageid=idFirst");
      assertThat(request.describe()).isEqualTo("Removing package 1 of 2");
      assertThat(request.readTimeout()).isEqualTo(Duration.ofMinutes(2));
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(new Progress(0, UpdatePhase.REMOVING, null, null, "idFirst")));
    }

    @Test
    @DisplayName("an error of the device at the start fails the removal, which has removed nothing")
    void anErrorAtTheStart() {
      var dialogue = unchecked("idFirst");
      dialogue.start();

      var step = dialogue.answerWithError("UpdateStart");

      assertThat(failed(step).message()).isEqualTo("Error - something failed");
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.removed()).isEmpty());
    }

    @Test
    @DisplayName("an answer about something else is not what was expected")
    void anotherElement() {
      var dialogue = unchecked("idFirst");
      dialogue.start();

      notExpected("update--wrong-element.xml", ResponseElement.DELETE_REQUEST, dialogue);
    }

    @Test
    @DisplayName(
        "an answer that is not XML fails the removal, even a blank one: only the end may be blank")
    void notXmlAtTheStart() {
      var dialogue = unchecked("idFirst");
      dialogue.start();

      var step = dialogue.answerWithText("update--empty.txt");

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
    }
  }

  @Nested
  @DisplayName("the removals")
  class Removals {

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"DeleteRequest", "DeleteProgress"})
    @DisplayName(
        "each package that was removed asks for the next, with the percent of the packages that are"
            + " done, whichever of the two elements the device answered with")
    void theNextPackageIsRemoved(String element) {
      var dialogue = removing("idFirst", "idOther");

      var step = dialogue.answer(element, "Success");

      assertThat(form(step).encodedBody()).isEqualTo("type=bar&packageid=idOther");
      assertThat(form(step).describe()).isEqualTo("Removing package 2 of 2");
      assertThat(effects(step))
          .hasSize(1)
          .first()
          .isInstanceOfSatisfying(
              StepEffect.Report.class,
              report -> {
                assertThat(report.progress().percent()).isEqualTo(50);
                assertThat(report.progress().phase()).isEqualTo(UpdatePhase.REMOVING);
              });
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Success", "Complete", "success"})
    @DisplayName("a status of success is a package removed, and so is no status at all")
    void aSuccessIsARemoval(String status) {
      assertThat(form(removing("idFirst", "idOther").answer("DeleteRequest", status)).encodedBody())
          .isEqualTo("type=bar&packageid=idOther");
      assertThat(
              form(removing("idFirst", "idOther").answerWithoutStatus("DeleteRequest"))
                  .encodedBody())
          .isEqualTo("type=bar&packageid=idOther");
    }

    @Test
    @DisplayName(
        "the last package that was removed asks the device to finish, for an answer that may be"
            + " empty, reporting 100 percent")
    void theLastPackageAsksToFinish() {
      var step = removing("idFirst").answer("DeleteRequest", "Success");

      var finish = form(step);
      assertThat(finish.encodedBody()).isEqualTo("type=bar&status=success");
      assertThat(finish.describe()).isEqualTo("Finishing the removal");
      assertThat(finish.emptyAnswerIsNormal()).isTrue();
      assertThat(finish.readTimeout()).isEqualTo(Duration.ofMinutes(2));
      assertThat(effects(step))
          .containsExactly(
              new StepEffect.Report(
                  new Progress(100, UpdatePhase.REMOVING, null, null, "idFirst")));
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"InProgress", "Ready", "Strange"})
    @DisplayName(
        "a status that is not a success fails the removal, with the status and the package, and"
            + " keeps what was removed")
    void aStatusThatIsNotASuccess(String status) {
      var dialogue = removing("idFirst", "idOther");
      dialogue.answer("DeleteRequest", "Success");

      var step = dialogue.answer("DeleteProgress", status);

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failed(step).message())
          .isEqualTo("The device answered a removal with a status that is not a success");
      assertThat(failed(step).details())
          .containsEntry("status", status)
          .containsEntry("packageId", "idOther");
      assertThat(partial(step))
          .hasValueSatisfying(result -> assertThat(result.removed()).containsExactly("idFirst"));
    }

    @Test
    @DisplayName("an error status is an error of the device, and fails the removal as such")
    void anErrorStatus() {
      assertThat(failed(removing("idFirst").answer("DeleteRequest", "Error")).code())
          .isEqualTo(CoreErrorCode.COMMAND_FAILED);
    }

    @Test
    @DisplayName("an error of the device fails the removal with that error")
    void anErrorOfTheDevice() {
      assertThat(failed(removing("idFirst").answerWithError("DeleteRequest")).message())
          .isEqualTo("Error - something failed");
    }

    @Test
    @DisplayName("an answer about something else is not what was expected")
    void anotherElement() {
      notExpected("update--wrong-element.xml", ResponseElement.DELETE_REQUEST, removing("idFirst"));
    }
  }

  @Nested
  @DisplayName("the end")
  class End {

    private Dialogue<UninstallResult> finishing() {
      var dialogue = removing("idFirst", "idOther");
      dialogue.answer("DeleteRequest", "Success");
      dialogue.answer("DeleteProgress", "Success");
      return dialogue;
    }

    @Test
    @DisplayName(
        "a blank answer that is not XML ends it: this is how the device ends a removal, with"
            + " nothing")
    void aBlankAnswerEndsIt() {
      var result = finished(finishing().answerWithText("update--empty.txt"));

      assertThat(result.removed()).containsExactly("idFirst", "idOther");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"Success", "Complete", "success"})
    @DisplayName("an end with a status of success ends it, with the packages that were removed")
    void aSuccessEndsIt(String status) {
      assertThat(finished(finishing().answer("UpdateEnd", status)).removed())
          .containsExactly("idFirst", "idOther");
    }

    @Test
    @DisplayName("an end with no status ends it too")
    void noStatusEndsIt() {
      assertThat(finished(finishing().answerWithoutStatus("UpdateEnd")).removed()).hasSize(2);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"InProgress", "Ready", "Strange"})
    @DisplayName(
        "an end with another status is not a success: it is said, and what was removed is kept")
    void anotherStatus(String status) {
      var step = finishing().answer("UpdateEnd", status);

      assertThat(failed(step).code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failed(step).message())
          .isEqualTo("The device ended the removal with a status that is not a success");
      assertThat(failed(step).details()).containsEntry("status", status);
      assertThat(partial(step))
          .hasValueSatisfying(
              result -> assertThat(result.removed()).containsExactly("idFirst", "idOther"));
    }

    @Test
    @DisplayName("an end with an error, or with an error status, is an error of the device")
    void anErrorAtTheEnd() {
      assertThat(failed(finishing().answerWithError("UpdateEnd")).message())
          .isEqualTo("Error - something failed");
      assertThat(failed(finishing().answer("UpdateEnd", "Error")).code())
          .isEqualTo(CoreErrorCode.COMMAND_FAILED);
    }

    @Test
    @DisplayName(
        "an answer that is not XML and not blank fails the removal at its end, with the words for"
            + " that")
    void notXmlAtTheEnd() {
      var step = finishing().answerWithText("update--not-xml.txt");

      var answer =
          new CgiResponse(
              200,
              "text/html",
              Resources.text(UninstallConversationTest.class, "update--not-xml.txt"),
              null);
      assertThat(failed(step)).isEqualTo(ResponseFailures.notXml(answer));
      assertThat(partial(step))
          .hasValueSatisfying(
              result -> assertThat(result.removed()).containsExactly("idFirst", "idOther"));
    }
  }
}
