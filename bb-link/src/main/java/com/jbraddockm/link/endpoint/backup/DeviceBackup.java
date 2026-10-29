package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.actions.DevicePin;
import com.jbraddockm.link.endpoint.actions.GetPin;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Backs the device up into a {@code .bbb} file: it reads the categories, downloads the selected ones
 * to temporary files, records the device's PIN and software version, and writes the archive. The
 * temporary files are removed whether it succeeds or fails.
 *
 * <p>The device must hold a backup-and-restore key in its session for the data to stream; how the
 * key gets there is outside bb-link (see the backup analysis). Without it, a category streams empty
 * and the command fails with {@code NO_BACKUP_DATA}.
 */
public final class DeviceBackup {

  /** Creates the orchestrator. */
  public DeviceBackup() {}

  /**
   * The command to replay recorded answers through for {@code -parse}: the category list, which
   * exercises the {@code BackupList} parser without a device.
   *
   * @return a fresh read-only command
   */
  public static com.jbraddockm.link.transport.cgi.DeviceCommand<?> replayCommand() {
    return new ListCategories();
  }

  /**
   * Runs a backup.
   *
   * @param session a session to the device
   * @param request what to back up and where
   * @param listener receives progress and log events
   * @return the archive that was written, or why it was not
   */
  public CommandOutcome<BackupResult> run(
      DeviceSession session, BackupRequest request, CommandListener listener) {
    BackupInventory inventory;
    switch (session.execute(new ListCategories(), listener)) {
      case CommandOutcome.Succeeded<BackupInventory>(var result, _) -> inventory = result;
      case CommandOutcome.Failed<BackupInventory>(var error, _, _) -> {
        return CommandOutcome.failed(error);
      }
    }

    var selected = resolve(request, inventory);
    if (selected instanceof Resolution.Refused(var error)) {
      return CommandOutcome.failed(error);
    }
    var categories = ((Resolution.Selected) selected).categories();

    Path tempDir;
    try {
      tempDir = Files.createTempDirectory("bb-link-backup");
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR,
              "Could not make a temporary folder for the backup: " + failure.getMessage()));
    }

    var targets = new ArrayList<CategoryTarget>();
    for (var category : categories) {
      targets.add(new CategoryTarget(category, tempDir.resolve(category.wireName() + ".tar")));
    }

    try {
      BackupStreamResult streamed;
      switch (session.execute(BackupStream.of(targets), listener)) {
        case CommandOutcome.Succeeded<BackupStreamResult>(var result, _) -> streamed = result;
        case CommandOutcome.Failed<BackupStreamResult>(var error, _, _) -> {
          return CommandOutcome.failed(error);
        }
      }

      listener.onProgress(Progress.ofPercent(0, BackupPhase.PACKAGING));
      var pin = readPin(session, listener);
      var softwareVersion = readSoftwareVersion(session, listener);
      var manifest = BackupManifest.of(pin, softwareVersion, streamed.categories());
      var entries =
          streamed.categories().stream()
              .map(
                  downloaded ->
                      new BbbArchive.ArchiveEntry(
                          downloaded.category().tarEntryName(), downloaded.file()))
              .toList();
      try {
        BbbArchive.write(request.outputFile(), manifest, entries);
      } catch (IOException failure) {
        return CommandOutcome.failed(
            new ServiceError(
                CoreErrorCode.IO_ERROR,
                "Could not write the backup file "
                    + request.outputFile()
                    + ": "
                    + failure.getMessage()));
      }
      listener.onProgress(Progress.ofPercent(100, BackupPhase.PACKAGING));
      var totalBytes = streamed.categories().stream().mapToLong(DownloadedCategory::bytes).sum();
      var categoryBytes =
          streamed.categories().stream()
              .collect(
                  java.util.stream.Collectors.toMap(
                      downloaded -> downloaded.category().wireName(),
                      DownloadedCategory::bytes,
                      (a, b) -> a,
                      java.util.LinkedHashMap::new));
      return CommandOutcome.succeeded(
          new BackupResult(request.outputFile(), totalBytes, pin, softwareVersion, categoryBytes));
    } finally {
      deleteQuietly(targets.stream().map(CategoryTarget::destination).toList(), tempDir, listener);
    }
  }

  /** Decides which categories to back up: the request's, or all offered when none were named. */
  private static Resolution resolve(BackupRequest request, BackupInventory inventory) {
    var offered = inventory.categories().stream().map(AvailableCategory::category).toList();
    if (request.categories().isEmpty()) {
      if (offered.isEmpty()) {
        return new Resolution.Refused(
            new ServiceError(
                CoreErrorCode.COMMAND_FAILED, "The device offered no categories to back up"));
      }
      return new Resolution.Selected(offered);
    }
    var missing =
        request.categories().stream().filter(category -> !offered.contains(category)).toList();
    if (!missing.isEmpty()) {
      return new Resolution.Refused(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The device does not offer "
                  + missing.stream().map(BackupCategory::wireName).toList()
                  + " to back up",
              Json.object(
                  "offered", offered.stream().map(BackupCategory::wireName).toList())));
    }
    return new Resolution.Selected(request.categories());
  }

  private static String readPin(DeviceSession session, CommandListener listener) {
    return switch (session.execute(new GetPin(), listener)) {
      case CommandOutcome.Succeeded<DevicePin>(var pin, _) -> pin.pin();
      case CommandOutcome.Failed<DevicePin> _ -> null;
    };
  }

  private static String readSoftwareVersion(DeviceSession session, CommandListener listener) {
    return switch (session.execute(new GetDeviceProperties(), listener)) {
      case CommandOutcome.Succeeded<DeviceProperties>(var properties, _) ->
          properties.operatingSystem().map(os -> os.version()).orElse(null);
      case CommandOutcome.Failed<DeviceProperties> _ -> null;
    };
  }

  private static void deleteQuietly(List<Path> files, Path directory, CommandListener listener) {
    for (var file : files) {
      try {
        Files.deleteIfExists(file);
      } catch (IOException failure) {
        listener.onLog(
            LogLevel.DEBUG, "Could not remove a temporary backup file: " + failure.getMessage());
      }
    }
    try {
      Files.deleteIfExists(directory);
    } catch (IOException failure) {
      listener.onLog(
          LogLevel.DEBUG, "Could not remove the temporary backup folder: " + failure.getMessage());
    }
  }

  /** The outcome of choosing which categories to back up. */
  private sealed interface Resolution {
    record Selected(List<BackupCategory> categories) implements Resolution {}

    record Refused(ServiceError error) implements Resolution {}
  }
}
