package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.ServiceError;
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

/// The conversation of a restore: begin, upload each category's archive, and finish.
///
/// ```
/// action=Restore&mode=<categories>&totalsize=<E>            → RestoreStart Ready
/// per category:  ?action=Restore&type=<category>&size=<n>   → RestoreSend Success  (body = archive)
/// status=success                                            → BackupEnd  (may be empty: it resets)
/// ```
///
/// The finishing step is tolerant of an empty answer, because the device resets to apply the
/// restore and may drop the connection as it does.
final class RestoreStreamConversation implements Conversation<RestoreStreamResult> {

  /** How long the device may stay silent while it takes a category or works on one. */
  private static final Duration SLOW_STEP = Duration.ofMinutes(10);

  private final List<UploadTarget> targets;
  private final long totalSize;
  private final List<BackupCategory> restored = new ArrayList<>();
  private Stage stage = Stage.BEGIN;
  private int index;
  private boolean resetRequired;
  private String lastAnswer = "";

  RestoreStreamConversation(List<UploadTarget> targets, long totalSize) {
    this.targets = List.copyOf(targets);
    this.totalSize = totalSize;
  }

  @Override
  public Step<RestoreStreamResult> start() {
    var mode = BackupCategory.modeValue(targets.stream().map(UploadTarget::category).toList());
    return new Step.Send<>(
        CgiRequest.Form.to(Endpoint.BACKUP)
            .with(FormField.ACTION, BackupAction.RESTORE)
            .withText(FormField.MODE, mode)
            .withText(FormField.TOTAL_SIZE, Long.toString(totalSize))
            .describedAs("Starting the restore")
            .waitingUpTo(SLOW_STEP));
  }

  @Override
  public Step<RestoreStreamResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (!response.body().isEmpty()) {
      lastAnswer = response.body();
    }
    return switch (stage) {
      case BEGIN -> begun(response, envelope);
      case UPLOADING -> uploaded(response, envelope);
      case FINISHING -> finished(response, envelope);
    };
  }

  private Step<RestoreStreamResult> begun(CgiResponse response, ResponseEnvelope envelope) {
    return Refusals.check(response, envelope, ResponseElement.RESTORE_START)
        .map(this::fail)
        .orElseGet(
            () -> {
              stage = Stage.UPLOADING;
              index = 0;
              return new Step.Send<>(upload(0), List.of(report(0)));
            });
  }

  private Step<RestoreStreamResult> uploaded(CgiResponse response, ResponseEnvelope envelope) {
    return Refusals.check(response, envelope, ResponseElement.RESTORE_SEND)
        .map(this::fail)
        .orElseGet(() -> accepted(envelope));
  }

  /** Records an accepted upload and asks for the next category, or finishes. */
  private Step<RestoreStreamResult> accepted(ResponseEnvelope envelope) {
    resetRequired |=
        envelope.payload().childText("ResetRequired").orElse("").strip().equalsIgnoreCase("true");
    restored.add(targets.get(index).category());
    index++;
    if (index < targets.size()) {
      return new Step.Send<>(upload(index), List.of(report(100)));
    }
    stage = Stage.FINISHING;
    return new Step.Send<>(
        CgiRequest.Form.to(Endpoint.BACKUP)
            .withText(FormField.STATUS, "success")
            .describedAs("Finishing the restore")
            .expectingNoBody()
            .waitingUpTo(SLOW_STEP),
        List.of(report(100)));
  }

  private Step<RestoreStreamResult> finished(CgiResponse response, ResponseEnvelope envelope) {
    // The device resets to apply the restore and may answer with nothing: an empty answer here is a
    // success, not a lost session.
    if (envelope == null) {
      if (response.body().isBlank()) {
        return finish();
      }
      return fail(ResponseFailures.notXml(response));
    }
    var reported = ResponseFailures.reportedError(envelope);
    return reported.map(this::fail).orElseGet(this::finish);
  }

  private CgiRequest upload(int at) {
    var target = targets.get(at);
    return CgiRequest.Upload.of(
            Endpoint.BACKUP,
            target.file(),
            target.size(),
            BackupPhase.UPLOADING,
            target.category().wireName())
        .with(QueryParameter.ACTION, BackupAction.RESTORE)
        .with(QueryParameter.TYPE, target.category())
        .with(QueryParameter.SIZE, () -> Long.toString(target.size()))
        .describedAs("Uploading the " + target.category().wireName() + " category")
        .waitingUpTo(SLOW_STEP);
  }

  private StepEffect.Report report(int percent) {
    var item = index < targets.size() ? targets.get(index).category().wireName() : null;
    return new StepEffect.Report(
        new Progress(percent, BackupPhase.UPLOADING, null, totalSize, item));
  }

  private Step<RestoreStreamResult> finish() {
    return new Step.Finish<>(
        new RestoreStreamResult(restored, resetRequired, ResponseBody.of(lastAnswer)));
  }

  private Step<RestoreStreamResult> fail(ServiceError error) {
    return new Step.Fail<>(
        error,
        Optional.of(new RestoreStreamResult(restored, resetRequired, ResponseBody.of(lastAnswer))));
  }

  private enum Stage {
    BEGIN,
    UPLOADING,
    FINISHING
  }
}
