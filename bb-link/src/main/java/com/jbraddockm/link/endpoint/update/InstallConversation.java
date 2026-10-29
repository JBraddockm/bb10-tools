package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarKind;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.StepEffect;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The conversation of an installation: the device drives it, and each answer says what to send
 * next.
 *
 * <pre>
 * properties → start (mode, size) → UpdateStart
 *   for each bar:  upload → UpdateSend → poll → UpdateProgress … Success
 * finish (status=success) → UpdateEnd
 * </pre>
 *
 * <p>The layout of the answers is taken from research, and only the element and the {@code Status},
 * {@code Progress}, {@code ErrorIdentifier} and {@code ErrorDescription} are read. Anything that
 * does not fit ends the command with {@code UNEXPECTED_RESPONSE}, and the whole answer is kept in
 * the partial result, so that a first run against a device shows what it really says.
 */
final class InstallConversation implements Conversation<InstallResult> {

  /** What the device needs beyond the size of the packages, according to the research. */
  private static final int SPACE_FACTOR_PERCENT = 150;

  /** How long the device may stay silent while it takes a package or works on one. */
  private static final Duration SLOW_STEP = Duration.ofMinutes(10);

  /** How long an installation may take altogether, when it reports progress. */
  private static final Duration LONGEST_WAIT = Duration.ofMinutes(30);
  private final List<BarInfo> bars;
  private final boolean firmware;
  private final Duration pollInterval;
  private final long maximumPolls;
  private final List<InstalledPackage> installed = new ArrayList<>();
  private Stage stage = Stage.CHECKING_SPACE;
  private int index;
  private long polls;
  private String lastAnswer = "";
  InstallConversation(List<BarInfo> bars, boolean firmware, Duration pollInterval) {
    this.bars = List.copyOf(bars);
    this.firmware = firmware;
    this.pollInterval = pollInterval;
    this.maximumPolls = Math.max(1, LONGEST_WAIT.toMillis() / Math.max(1, pollInterval.toMillis()));
  }

  /** The question that moves the device on, and that asks how far it is: a form with no fields. */
  private static CgiRequest poll() {
    return CgiRequest.Form.to(Endpoint.UPDATE)
        .describedAs("Waiting for the device to install the package")
        .waitingUpTo(SLOW_STEP);
  }

  private static int percentOf(ResponseEnvelope answer) {
    return answer
        .payload()
        .childText("Progress")
        .flatMap(
            text -> {
              try {
                return Optional.of(Integer.parseInt(text.strip()));
              } catch (NumberFormatException _) {
                return Optional.empty();
              }
            })
        .map(value -> Math.clamp(value, 0, 100))
        .orElse(0);
  }

  @Override
  public Step<InstallResult> start() {
    return new Step.Send<>(GetDeviceProperties.request());
  }

  // --- the stages -------------------------------------------------------------------------------

  @Override
  public Step<InstallResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    lastAnswer = response.body();
    if (envelope == null) {
      return fail(ResponseFailures.notXml(response));
    }
    return switch (stage) {
      case CHECKING_SPACE -> checkSpace(envelope);
      case STARTING -> started(envelope);
      case SENDING -> received(envelope);
      case WAITING -> progressed(envelope);
      case FINISHING -> finished(envelope);
    };
  }

  private Step<InstallResult> checkSpace(ResponseEnvelope answer) {
    return switch (GetDeviceProperties.interpret(answer)) {
      case Step.Fail<DeviceProperties>(var error, _) -> fail(error);
      case Step.Finish<DeviceProperties>(var properties) -> spaceChecked(properties);
      case Step.Send<DeviceProperties> _ ->
          throw new IllegalStateException("reading the properties never asks for another request");
    };
  }

  private Step<InstallResult> spaceChecked(DeviceProperties properties) {
    var total = totalBytes();
    var needed = total + total * (SPACE_FACTOR_PERCENT - 100) / 100;
    var free = properties.freeApplicationSpace();
    var before = new ArrayList<StepEffect>();
    if (free == null) {
      before.add(
          new StepEffect.Note(
              LogLevel.WARN,
              "The device does not say how much space is free; the check of the space is skipped"));
    } else if (needed > free) {
      return fail(
          new ServiceError(
              LinkErrorCode.NOT_ENOUGH_SPACE,
              "The device does not have enough free space for the installation",
              Json.object("requiredBytes", needed, "freeBytes", free, "packageBytes", total)));
    }
    stage = Stage.STARTING;
    var mode = firmware ? UpdateMode.INSTALL_OPERATING_SYSTEM : UpdateMode.INSTALL_APPLICATIONS;
    return new Step.Send<>(
        CgiRequest.Form.to(Endpoint.UPDATE)
            .with(FormField.MODE, mode)
            .withText(FormField.SIZE, Long.toString(total))
            .describedAs("Starting the installation")
            .waitingUpTo(SLOW_STEP),
        before);
  }

  private Step<InstallResult> started(ResponseEnvelope answer) {
    var refusal = refused(answer, ResponseElement.UPDATE_START);
    if (refusal.isPresent()) {
      return refusal.get();
    }
    stage = Stage.SENDING;
    return upload();
  }

  private Step<InstallResult> received(ResponseEnvelope answer) {
    var refusal = refused(answer, ResponseElement.UPDATE_SEND);
    if (refusal.isPresent()) {
      return refusal.get();
    }
    stage = Stage.WAITING;
    polls = 0;
    return new Step.Send<>(poll(), List.of(report(0)));
  }

  private Step<InstallResult> progressed(ResponseEnvelope answer) {
    var failure = ResponseFailures.reportedError(answer);
    if (failure.isPresent()) {
      return fail(failure.get());
    }
    var element = answer.element().orElse(null);
    if (element == ResponseElement.UPDATE_END) {
      return packageDone(true);
    }
    if (element == ResponseElement.UPDATE_SEND) {
      return waitMore(0);
    }
    if (element != ResponseElement.UPDATE_PROGRESS) {
      return fail(ResponseFailures.notExpected(answer, ResponseElement.UPDATE_PROGRESS));
    }
    var text = answer.payload().childText("Status").orElse("");
    return UpdateStatus.of(text)
        .map(
            status ->
                switch (status) {
                  case READY, IN_PROGRESS -> waitMore(percentOf(answer));
                  case SUCCESS, COMPLETE -> packageDone(false);
                  case ERROR ->
                      fail(
                          new ServiceError(
                              CoreErrorCode.COMMAND_FAILED, "The device reported an error"));
                })
        .orElseGet(
            () ->
                fail(
                    new ServiceError(
                        LinkErrorCode.UNEXPECTED_RESPONSE,
                        "The device reported a progress status that this version does not know",
                        Json.object("status", text))));
  }

  // --- what the stages share --------------------------------------------------------------------

  private Step<InstallResult> finished(ResponseEnvelope answer) {
    var refusal = refused(answer, ResponseElement.UPDATE_END);
    if (refusal.isPresent()) {
      return refusal.get();
    }
    // A device says {@code Success} at the end **[verified]**. An end without a status is accepted;
    // an end with another status is not taken for a success.
    var text = answer.payload().childText("Status");
    if (text.isPresent()) {
      var status = UpdateStatus.of(text.get());
      if (status.isEmpty()
          || (status.get() != UpdateStatus.SUCCESS && status.get() != UpdateStatus.COMPLETE)) {
        return fail(
            new ServiceError(
                LinkErrorCode.UNEXPECTED_RESPONSE,
                "The device ended the installation with a status that is not a success",
                Json.object("status", text.get())));
      }
    }
    return new Step.Finish<>(new InstallResult(installed, ResponseBody.of(lastAnswer)));
  }

  /** Keeps the device waiting for a moment, and asks again how far it is. */
  private Step<InstallResult> waitMore(int percent) {
    polls++;
    if (polls > maximumPolls) {
      return fail(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The device did not finish installing the package in time",
              Json.object("waitedMinutes", LONGEST_WAIT.toMinutes())));
    }
    return new Step.Send<>(poll(), List.of(report(percent), new StepEffect.Pause(pollInterval)));
  }

  /** The device has installed the package that was sent last; send the next or finish. */
  private Step<InstallResult> packageDone(boolean endedEarly) {
    var finished = report(100);
    installed.add(InstalledPackage.of(bars.get(index)));
    index++;
    if (index < bars.size()) {
      if (endedEarly) {
        return fail(
            new ServiceError(
                LinkErrorCode.UNEXPECTED_RESPONSE,
                "The device ended the installation before all packages were sent"));
      }
      stage = Stage.SENDING;
      return new Step.Send<>(uploadRequest(), List.of(finished));
    }
    if (endedEarly) {
      return new Step.Finish<>(new InstallResult(installed, ResponseBody.of(lastAnswer)));
    }
    stage = Stage.FINISHING;
    return new Step.Send<>(
        CgiRequest.Form.to(Endpoint.UPDATE)
            .withText(FormField.STATUS, "success")
            .describedAs("Finishing the installation")
            .waitingUpTo(SLOW_STEP),
        List.of(finished));
  }

  /**
   * Checks that the answer is the element that was expected and does not report an error.
   *
   * @return the step that ends the command, or empty if the answer is fine
   */
  private Optional<Step<InstallResult>> refused(ResponseEnvelope answer, ResponseElement expected) {
    var failure = ResponseFailures.reportedError(answer);
    if (failure.isPresent()) {
      return Optional.of(fail(failure.get()));
    }
    if (answer.element().orElse(null) != expected) {
      return Optional.of(fail(ResponseFailures.notExpected(answer, expected)));
    }
    return Optional.empty();
  }

  private Step<InstallResult> fail(ServiceError error) {
    return new Step.Fail<>(
        error, Optional.of(new InstallResult(installed, ResponseBody.of(lastAnswer))));
  }

  private Step<InstallResult> upload() {
    return new Step.Send<>(uploadRequest());
  }

  private CgiRequest uploadRequest() {
    var bar = bars.get(index);
    var kind = bar.kind() == BarKind.RADIO ? UploadKind.RADIO : UploadKind.BAR;
    return CgiRequest.Upload.of(
            Endpoint.UPDATE, bar.file(), bar.sizeBytes(), UpdatePhase.UPLOADING, nameOfCurrent())
        .with(QueryParameter.TYPE, kind)
        .describedAs("Sending package " + (index + 1) + " of " + bars.size())
        .waitingUpTo(SLOW_STEP);
  }

  private StepEffect.Report report(int percent) {
    return new StepEffect.Report(
        new Progress(percent, UpdatePhase.INSTALLING, null, null, nameOfCurrent()));
  }

  private String nameOfCurrent() {
    var name = bars.get(index).file().getFileName();
    return name == null ? "" : name.toString();
  }

  private long totalBytes() {
    return bars.stream().mapToLong(BarInfo::sizeBytes).sum();
  }

  private enum Stage {
    CHECKING_SPACE,
    STARTING,
    SENDING,
    WAITING,
    FINISHING
  }
}
