package com.jbraddockm.link.update;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseBody;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.StepEffect;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.properties.PackageType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The conversation of a removal.
 *
 * <pre>
 * properties (unless the check is off) → start (mode=app, size=0) → UpdateStart              [verified]
 *   for each package:  type=bar, packageid → DeleteRequest (the first), DeleteProgress (the
 *                      others), each with Status Success                                  [verified]
 * finish (type=bar, status=success) → UpdateEnd, Status Success                        [verified]
 * </pre>
 *
 * <p>All of it has been seen on a device. The removal takes effect at its own request. The device
 * does not check the id: it answers {@code Success} for a package that is not installed, so the
 * check against the package list is the only protection against a mistyped id. The research named
 * {@code DeleteRequest} for the start and {@code DeleteProgress} for every removal; both names are
 * accepted wherever they appear. An empty answer to the finish is accepted as well, and whatever
 * the device says is kept in the result.
 */
final class UninstallConversation implements Conversation<UninstallResult> {

  /** How long the device may stay silent while it works on a step. */
  private static final Duration SLOW_STEP = Duration.ofMinutes(2);

  private enum Stage {
    CHECKING,
    STARTING,
    REMOVING,
    FINISHING
  }

  private final List<String> packageIds;
  private final boolean check;
  private final List<String> removed = new ArrayList<>();
  private Stage stage;
  private int index;
  private String lastAnswer = "";

  UninstallConversation(List<String> packageIds, boolean check) {
    this.packageIds = List.copyOf(packageIds);
    this.check = check;
    this.stage = check ? Stage.CHECKING : Stage.STARTING;
  }

  @Override
  public Step<UninstallResult> start() {
    return new Step.Send<>(check ? GetDeviceProperties.request() : startRequest());
  }

  @Override
  public Step<UninstallResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    lastAnswer = response.body();
    if (envelope == null) {
      // The end of a removal is not known to answer anything, so nothing is a fine answer to it.
      return stage == Stage.FINISHING && response.body().isBlank()
          ? finish()
          : fail(ResponseFailures.notXml(response));
    }
    var answer = envelope;
    return switch (stage) {
      case CHECKING -> checked(answer);
      case STARTING -> started(answer);
      case REMOVING -> removedOne(answer);
      case FINISHING -> finished(answer);
    };
  }

  // --- the stages -------------------------------------------------------------------------------

  private Step<UninstallResult> checked(ResponseEnvelope answer) {
    return switch (GetDeviceProperties.interpret(answer)) {
      case Step.Fail<DeviceProperties>(var error, _) -> fail(error);
      case Step.Finish<DeviceProperties>(var properties) ->
          refusal(properties).map(this::fail).orElseGet(this::begin);
      case Step.Send<DeviceProperties> _ ->
          throw new IllegalStateException("reading the properties never asks for another request");
    };
  }

  private Step<UninstallResult> started(ResponseEnvelope answer) {
    var failure = ResponseFailures.reportedError(answer);
    if (failure.isPresent()) {
      return fail(failure.get());
    }
    var element = answer.element().orElse(null);
    if (element != ResponseElement.DELETE_REQUEST && element != ResponseElement.UPDATE_START) {
      return fail(ResponseFailures.notExpected(answer, ResponseElement.DELETE_REQUEST));
    }
    stage = Stage.REMOVING;
    return removal();
  }

  private Step<UninstallResult> removedOne(ResponseEnvelope answer) {
    var failure = ResponseFailures.reportedError(answer);
    if (failure.isPresent()) {
      return fail(failure.get());
    }
    // A device answers the first removal of a conversation with DeleteRequest and the others with
    // DeleteProgress, each with a status **[verified]**. Either is accepted for any of them.
    var element = answer.element().orElse(null);
    if (element != ResponseElement.DELETE_REQUEST && element != ResponseElement.DELETE_PROGRESS) {
      return fail(ResponseFailures.notExpected(answer, ResponseElement.DELETE_REQUEST));
    }
    var text = answer.payload().childText("Status");
    if (text.isPresent() && !isSuccess(text.get())) {
      return fail(
          new ServiceError(
              LinkErrorCode.UNEXPECTED_RESPONSE,
              "The device answered a removal with a status that is not a success",
              Json.object("status", text.get(), "packageId", packageIds.get(index))));
    }
    removed.add(packageIds.get(index));
    index++;
    if (index < packageIds.size()) {
      return removal();
    }
    stage = Stage.FINISHING;
    return new Step.Send<>(finishRequest(), List.of(report(100)));
  }

  private static boolean isSuccess(String text) {
    return UpdateStatus.of(text)
        .filter(status -> status == UpdateStatus.SUCCESS || status == UpdateStatus.COMPLETE)
        .isPresent();
  }

  private Step<UninstallResult> finished(ResponseEnvelope answer) {
    var failure = ResponseFailures.reportedError(answer);
    if (failure.isPresent()) {
      return fail(failure.get());
    }
    // A device ends it with UpdateEnd and Success **[verified]**. An answer without a status is
    // accepted; one with another status is not taken for a success.
    var text = answer.payload().childText("Status");
    if (text.isPresent() && !isSuccess(text.get())) {
      return fail(
          new ServiceError(
              LinkErrorCode.UNEXPECTED_RESPONSE,
              "The device ended the removal with a status that is not a success",
              Json.object("status", text.get())));
    }
    return finish();
  }

  // --- what the stages share --------------------------------------------------------------------

  /** Why the package list says that a removal cannot be tried, if it does. */
  private Optional<ServiceError> refusal(DeviceProperties properties) {
    for (var position = 0; position < packageIds.size(); position++) {
      var packageId = packageIds.get(position);
      var installed =
          properties.packages().stream()
              .filter(entry -> packageId.equals(entry.packageId()))
              .findFirst();
      var parameter = "packageIds[" + position + "]";
      if (installed.isEmpty()) {
        return Optional.of(
            new InvalidArgumentException(parameter, "is not installed on the device")
                .toServiceError());
      }
      if (installed.get().type() != PackageType.APPLICATION) {
        return Optional.of(
            new InvalidArgumentException(
                    parameter, "is not an application; removing firmware is not supported")
                .toServiceError());
      }
    }
    return Optional.empty();
  }

  private Step<UninstallResult> begin() {
    stage = Stage.STARTING;
    return new Step.Send<>(startRequest());
  }

  private static CgiRequest startRequest() {
    return CgiRequest.Form.to(Endpoint.UPDATE)
        .with(FormField.MODE, UpdateMode.UNINSTALL_APPLICATIONS)
        .withText(FormField.SIZE, "0")
        .describedAs("Starting the removal")
        .waitingUpTo(SLOW_STEP);
  }

  private Step<UninstallResult> removal() {
    var request =
        CgiRequest.Form.to(Endpoint.UPDATE)
            .with(FormField.TYPE, UploadKind.BAR)
            .withText(FormField.PACKAGE_ID, packageIds.get(index))
            .describedAs("Removing package " + (index + 1) + " of " + packageIds.size())
            .waitingUpTo(SLOW_STEP);
    return new Step.Send<>(request, List.of(report(index * 100 / packageIds.size())));
  }

  private static CgiRequest finishRequest() {
    return CgiRequest.Form.to(Endpoint.UPDATE)
        .with(FormField.TYPE, UploadKind.BAR)
        .withText(FormField.STATUS, "success")
        .describedAs("Finishing the removal")
        .expectingNoBody()
        .waitingUpTo(SLOW_STEP);
  }

  private StepEffect.Report report(int percent) {
    var item = packageIds.get(Math.min(index, packageIds.size() - 1));
    return new StepEffect.Report(new Progress(percent, UpdatePhase.REMOVING, null, null, item));
  }

  private Step<UninstallResult> finish() {
    return new Step.Finish<>(partialResult());
  }

  private Step<UninstallResult> fail(ServiceError error) {
    return new Step.Fail<>(error, Optional.of(partialResult()));
  }

  private UninstallResult partialResult() {
    return new UninstallResult(removed, ResponseBody.of(lastAnswer));
  }
}
