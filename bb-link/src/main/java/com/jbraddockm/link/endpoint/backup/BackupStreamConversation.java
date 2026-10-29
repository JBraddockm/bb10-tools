package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
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

/// The conversation of a backup: select the categories, wait while the device prepares the archive,
/// then stream each category to its file, and finish.
///
/// ```
/// mode=<categories>         → BackupStart
/// query=activity (poll)     → BackupStartActivity InProgress… Ready (+ TotalSize)
/// for each category:  type=<category>  → the archive, streamed to a file
/// status=success            → BackupEnd
/// ```
///
/// The data step's answer is the archive itself, which the transport streams to a file; the
/// conversation reads {@link CgiResponse#downloaded()}, not the body. An empty archive means the
/// device had no backup key and ends the command with {@link LinkErrorCode#NO_BACKUP_DATA}.
final class BackupStreamConversation implements Conversation<BackupStreamResult> {

  /** How long the device may stay silent while it streams a category. */
  private static final Duration SLOW_STEP = Duration.ofMinutes(10);

  /** How long the device may take to prepare the archive before it is given up. */
  private static final Duration LONGEST_PREPARE = Duration.ofMinutes(30);

  private final List<CategoryTarget> targets;
  private final List<PackageType> appPackageTypes;
  private final boolean rev2;
  private final Duration pollInterval;
  private final long maximumPolls;
  private final List<DownloadedCategory> downloaded = new ArrayList<>();
  private Stage stage = Stage.STARTING;
  private int index;
  private long polls;
  private long totalSize;
  private String lastAnswer = "";

  BackupStreamConversation(
      List<CategoryTarget> targets, List<PackageType> appPackageTypes, Duration pollInterval) {
    this.targets = List.copyOf(targets);
    this.appPackageTypes = List.copyOf(appPackageTypes);
    this.rev2 = !this.appPackageTypes.isEmpty();
    this.pollInterval = pollInterval;
    this.maximumPolls =
        Math.max(1, LONGEST_PREPARE.toMillis() / Math.max(1, pollInterval.toMillis()));
  }

  private static long readTotalSize(ResponseEnvelope envelope) {
    return envelope
        .payload()
        .childText("TotalSize")
        .flatMap(
            text -> {
              try {
                return Optional.of(Long.parseLong(text.strip()));
              } catch (NumberFormatException _) {
                return Optional.empty();
              }
            })
        .orElse(0L);
  }

  @Override
  public Step<BackupStreamResult> start() {
    var mode = BackupCategory.modeValue(targets.stream().map(CategoryTarget::category).toList());
    var form = CgiRequest.Form.to(Endpoint.BACKUP).withText(FormField.MODE, mode);
    if (rev2) {
      form = form.withText(FormField.OPT, "rev2");
    }
    return new Step.Send<>(form.describedAs("Starting the backup").waitingUpTo(SLOW_STEP));
  }

  @Override
  public Step<BackupStreamResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() == null && !response.body().isEmpty()) {
      lastAnswer = response.body();
    }
    return switch (stage) {
      case STARTING -> started(response, envelope);
      case PREPARING -> prepared(response, envelope);
      case DOWNLOADING -> received(response, envelope);
      case FINISHING -> finished(response, envelope);
    };
  }

  private Step<BackupStreamResult> started(CgiResponse response, ResponseEnvelope envelope) {
    return Refusals.check(response, envelope, ResponseElement.BACKUP_START)
        .map(this::fail)
        .orElseGet(
            () -> {
              stage = Stage.PREPARING;
              polls = 0;
              return new Step.Send<>(activityPoll(), List.of(report(BackupPhase.PREPARING)));
            });
  }

  private Step<BackupStreamResult> prepared(CgiResponse response, ResponseEnvelope envelope) {
    var refusal = Refusals.check(response, envelope, ResponseElement.BACKUP_START_ACTIVITY);
    if (refusal.isPresent()) {
      return fail(refusal.get());
    }
    var status = BackupStatus.of(envelope.payload().childText("Status").orElse(""));
    return status
        .<Step<BackupStreamResult>>map(
            backupStatus ->
                switch (backupStatus) {
                  case IN_PROGRESS, READY -> {
                    if (backupStatus == BackupStatus.READY) {
                      totalSize = readTotalSize(envelope);
                      stage = Stage.DOWNLOADING;
                      index = 0;
                      yield new Step.Send<>(
                          download(0), List.of(report(BackupPhase.DOWNLOADING)));
                    }
                    yield pollAgain();
                  }
                  case SUCCESS -> {
                    // Some firmware may report the archive ready as Success rather than Ready.
                    totalSize = readTotalSize(envelope);
                    stage = Stage.DOWNLOADING;
                    index = 0;
                    yield new Step.Send<>(download(0), List.of(report(BackupPhase.DOWNLOADING)));
                  }
                  case ERROR ->
                      fail(
                          ResponseFailures.notExpected(
                              envelope, ResponseElement.BACKUP_START_ACTIVITY));
                })
        .orElseGet(
            () ->
                fail(
                    new ServiceError(
                        LinkErrorCode.UNEXPECTED_RESPONSE,
                        "The device reported a backup status that this version does not know",
                        Json.object("status", envelope.payload().childText("Status").orElse("")))));
  }

  private Step<BackupStreamResult> received(CgiResponse response, ResponseEnvelope envelope) {
    if (response.downloaded() != null) {
      var data = response.downloaded();
      if (data.bytes() == 0) {
        return fail(
            new ServiceError(
                LinkErrorCode.NO_BACKUP_DATA,
                "The device produced no data for the "
                    + targets.get(index).category().wireName()
                    + " category; its backup-and-restore key is missing or it is in the wrong state",
                Json.object("category", targets.get(index).category().wireName())));
      }
      downloaded.add(
          new DownloadedCategory(targets.get(index).category(), data.file(), data.bytes()));
      index++;
      if (index < targets.size()) {
        return new Step.Send<>(download(index), List.of(report(BackupPhase.DOWNLOADING)));
      }
      stage = Stage.FINISHING;
      return new Step.Send<>(
          CgiRequest.Form.to(Endpoint.BACKUP)
              .withText(FormField.STATUS, "success")
              .describedAs("Finishing the backup")
              .waitingUpTo(SLOW_STEP));
    }
    // Not the archive: an error the device reported as XML, or nothing at all.
    if (envelope != null) {
      return fail(
          ResponseFailures.reportedError(envelope)
              .orElseGet(
                  () ->
                      new ServiceError(
                          LinkErrorCode.UNEXPECTED_RESPONSE,
                          "The device answered the data request with "
                              + envelope.name()
                              + " instead of the archive",
                          Json.object("received", envelope.name()))));
    }
    return fail(
        new ServiceError(
            LinkErrorCode.NO_BACKUP_DATA,
            "The device produced no data for the "
                + targets.get(index).category().wireName()
                + " category; its backup-and-restore key is missing or it is in the wrong state",
            Json.object("category", targets.get(index).category().wireName())));
  }

  private Step<BackupStreamResult> finished(CgiResponse response, ResponseEnvelope envelope) {
    return Refusals.check(response, envelope, ResponseElement.BACKUP_END)
        .map(this::fail)
        .orElseGet(
            () ->
                new Step.Finish<>(
                    new BackupStreamResult(downloaded, totalSize, ResponseBody.of(lastAnswer))));
  }

  private Step<BackupStreamResult> pollAgain() {
    polls++;
    if (polls > maximumPolls) {
      return fail(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The device did not finish preparing the backup in time",
              Json.object("waitedMinutes", LONGEST_PREPARE.toMinutes())));
    }
    return new Step.Send<>(
        activityPoll(),
        List.of(report(BackupPhase.PREPARING), new StepEffect.Pause(pollInterval)));
  }

  private CgiRequest activityPoll() {
    // opt=rev2 goes on the mode= start only; the proven wire sends plain activity/type steps, and
    // opt=rev2 on the data step makes the device return an empty stream.
    return CgiRequest.Form.to(Endpoint.BACKUP)
        .withText(FormField.QUERY, "activity")
        .describedAs("Waiting for the device to prepare the backup")
        .waitingUpTo(SLOW_STEP);
  }

  private CgiRequest download(int at) {
    var target = targets.get(at);
    var request =
        CgiRequest.Download.to(
                Endpoint.BACKUP,
                target.destination(),
                0,
                BackupPhase.DOWNLOADING,
                target.category().wireName())
            .with(FormField.TYPE, target.category());
    // pkgtype sub-selects the app category only; media and settings carry none. No opt=rev2 here —
    // it belongs on the mode= start and breaks the data stream if repeated.
    if (rev2 && target.category() == BackupCategory.APP) {
      request = request.withText(FormField.PKGTYPE, PackageType.csv(appPackageTypes));
    }
    return request
        .describedAs("Downloading the " + target.category().wireName() + " category")
        .waitingUpTo(SLOW_STEP);
  }

  private StepEffect.Report report(BackupPhase phase) {
    // Phase-transition markers report 0%; the byte-level percentage comes from the transport's
    // download observer (ConversationRunner.observerFor).
    var item = index < targets.size() ? targets.get(index).category().wireName() : null;
    return new StepEffect.Report(new Progress(0, phase, null, null, item));
  }

  private Step<BackupStreamResult> fail(ServiceError error) {
    return new Step.Fail<>(
        error,
        Optional.of(new BackupStreamResult(downloaded, totalSize, ResponseBody.of(lastAnswer))));
  }

  private enum Stage {
    STARTING,
    PREPARING,
    DOWNLOADING,
    FINISHING
  }
}
