package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Restores a {@code .bbb} file onto the device: it reads the manifest, extracts the selected
 * categories to temporary files, and uploads them. The device decrypts and applies each, then
 * resets.
 *
 * <p>Destructive. Nothing is sent to the device unless the request confirms the restore; the
 * confirmation is checked before the archive is even opened. The device must hold the same
 * backup-and-restore key the archive was made with, or it cannot decrypt what is uploaded.
 */
public final class DeviceRestore {

  /** Creates the orchestrator. */
  public DeviceRestore() {}

  /**
   * The command to replay recorded answers through for {@code -parse}: a restore with a throwaway
   * target, which exercises the {@code RestoreStart}/{@code RestoreSend}/{@code BackupEnd} parsers
   * without a device (the upload file is never read in replay).
   *
   * @return a fresh command
   */
  public static com.jbraddockm.link.transport.cgi.DeviceCommand<?> replayCommand() {
    return RestoreStream.of(List.of(new UploadTarget(BackupCategory.SETTINGS, Path.of("replay.tar"), 0)));
  }

  /**
   * Runs a restore.
   *
   * @param session a session to the device
   * @param request what to restore and whether it is confirmed
   * @param listener receives progress and log events
   * @return what was restored, or why it was not
   */
  public CommandOutcome<RestoreResult> run(
      DeviceSession session, RestoreRequest request, CommandListener listener) {
    if (!request.confirm()) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "Restore is destructive and resets the device; pass confirm=true to proceed"));
    }

    BackupManifest manifest;
    try {
      manifest = BbbArchive.readManifest(request.inputFile());
    } catch (IllegalArgumentException bad) {
      return CommandOutcome.failed(
          new ServiceError(
              LinkErrorCode.BBB_UNREADABLE,
              "Could not read " + request.inputFile() + ": " + bad.getMessage()));
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "Could not open " + request.inputFile() + ": " + failure.getMessage()));
    }

    var chosen = resolve(request, manifest);
    if (chosen instanceof Resolution.Refused(var error)) {
      return CommandOutcome.failed(error);
    }
    var entries = ((Resolution.Selected) chosen).entries();

    Path tempDir;
    try {
      tempDir = Files.createTempDirectory("bb-link-restore");
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "Could not make a temporary folder for the restore: " + failure.getMessage()));
    }

    var targets = new ArrayList<UploadTarget>();
    try {
      for (var entry : entries) {
        var file = tempDir.resolve(entry.category().wireName() + ".tar");
        long size;
        try {
          size = BbbArchive.extract(request.inputFile(), entry.fileName(), file);
        } catch (IllegalArgumentException bad) {
          return CommandOutcome.failed(
              new ServiceError(
                  LinkErrorCode.BBB_UNREADABLE,
                  "Could not read the backup: " + bad.getMessage()));
        } catch (IOException failure) {
          return CommandOutcome.failed(
              new ServiceError(
                  CoreErrorCode.IO_ERROR,
                  "Could not extract a category from the backup: " + failure.getMessage()));
        }
        targets.add(new UploadTarget(entry.category(), file, size));
      }

      return switch (session.execute(RestoreStream.of(targets), listener)) {
        case CommandOutcome.Succeeded<RestoreStreamResult>(var result, _) ->
            CommandOutcome.succeeded(
                new RestoreResult(result.restored(), result.resetRequired()));
        case CommandOutcome.Failed<RestoreStreamResult>(var error, _, _) ->
            CommandOutcome.failed(error);
      };
    } finally {
      deleteQuietly(targets.stream().map(UploadTarget::file).toList(), tempDir, listener);
    }
  }

  /** Decides which categories to restore: the request's, or all in the archive when none named. */
  private static Resolution resolve(RestoreRequest request, BackupManifest manifest) {
    var present = manifest.archives().stream().map(BackupManifest.Entry::category).toList();
    if (request.categories().isEmpty()) {
      return new Resolution.Selected(manifest.archives());
    }
    var missing =
        request.categories().stream().filter(category -> !present.contains(category)).toList();
    if (!missing.isEmpty()) {
      return new Resolution.Refused(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The backup does not contain "
                  + missing.stream().map(BackupCategory::wireName).toList(),
              Json.object("present", present.stream().map(BackupCategory::wireName).toList())));
    }
    var selected =
        manifest.archives().stream()
            .filter(entry -> request.categories().contains(entry.category()))
            .toList();
    return new Resolution.Selected(selected);
  }

  private static void deleteQuietly(List<Path> files, Path directory, CommandListener listener) {
    for (var file : files) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException failure) {
        listener.onLog(
            LogLevel.DEBUG, "Could not remove a temporary restore file: " + failure.getMessage());
      }
    }
    try {
      Files.deleteIfExists(directory);
    } catch (IOException failure) {
      listener.onLog(
          LogLevel.DEBUG, "Could not remove the temporary restore folder: " + failure.getMessage());
    }
  }

  /** The outcome of choosing which categories to restore. */
  private sealed interface Resolution {
    record Selected(List<BackupManifest.Entry> entries) implements Resolution {}

    record Refused(ServiceError error) implements Resolution {}
  }
}
