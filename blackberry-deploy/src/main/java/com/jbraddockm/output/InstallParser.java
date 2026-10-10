package com.jbraddockm.output;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Parses {@code -installApp}, with or without {@code -launchApp}.
 *
 * <p>All of the following was captured from a real device (exit status in brackets).
 *
 * <p>Release package, install and launch [0]:
 *
 * <pre>
 * Info: Application-Development-Mode is false, sync disabled
 * Info: Sending request: INSTALL_AND_LAUNCH
 * Info: Progress 100%...
 * actual_dname::...  actual_id::...  actual_version::...
 * result::success                      the verdict of the install
 * Info: Launching &lt;fullName&gt;...
 * result::188989588                    the verdict of the launch: a number, as for launchApp
 * </pre>
 *
 * <p>Corrupt package [2, nothing on stderr]: the three {@code actual_*} values are empty and the
 * last line is {@code result::failure 810 /var/tmp/...: cannot access member
 * 'META-INF/MANIFEST.MF'}.
 *
 * <p>Development-mode package: a first install prints {@code Info: [ERROR] Archive-Manifest-Version
 * ...} (the manifest-only package could not be built) and installs in full; a repeat install with
 * nothing changed prints {@code All files already in sync} and {@code File sync complete in 123ms}
 * without any install request or {@code actual_*} line; with the device's QConn service unreachable
 * it prints {@code Failed to obtain QConn connection, sync disabled} and installs in full.
 *
 * <p>Still provisional, derived from the tool's source and not yet captured: {@code File sync
 * failed} and {@code Attempt to use sync failed}, two install requests in one run, progress while
 * files are really being synchronised, and the rule that a launch verdict of {@code Invalid} after
 * a good install is a failure.
 */
final class InstallParser implements OutputParser<InstallResult> {

  private static final String PROGRESS_PREFIX = OutputText.INFO_PREFIX + "Progress ";
  private static final String REQUEST_PREFIX = "Sending request: ";
  private static final int MAX_PROGRESS_DIGITS = 3;
  private static final Pattern NUMBER = Pattern.compile("\\d+");

  @Override
  public Optional<Progress> progressOf(String line) {
    if (!line.startsWith(PROGRESS_PREFIX)) {
      return Optional.empty();
    }
    var remainder = line.substring(PROGRESS_PREFIX.length());
    var digits = (int) remainder.chars().takeWhile(Character::isDigit).count();
    return digits == 0 || digits > MAX_PROGRESS_DIGITS
        ? Optional.empty()
        : Optional.of(
            new Progress(Integer.parseInt(remainder.substring(0, digits)), Progress.Phase.INSTALL));
  }

  @Override
  public boolean parsesFailedRuns() {
    return true;
  }

  /**
   * The install worked but the device refused to launch the app, so the requested operation did not
   * complete.
   */
  @Override
  public Optional<CommandFailure> detectFailure(List<String> lines) {
    var scan = new Scan(lines);
    return scan.success && scan.launchRequested && "Invalid".equalsIgnoreCase(scan.lastResult)
        ? Optional.of(
            new CommandFailure(
                "The app was installed, but launching it failed: the device reported '"
                    + scan.lastResult
                    + "'",
                Map.of("result", scan.lastResult, "installed", true)))
        : Optional.empty();
  }

  @Override
  public InstallResult parse(List<String> lines) {
    var scan = new Scan(lines);
    var launchResult = scan.launchResult();
    return new InstallResult(
        scan.success,
        scan.fullName,
        OutputText.packageName(scan.fullName, scan.id).orElse(null),
        scan.id,
        scan.version,
        scan.strategy(),
        scan.nothingToSync,
        launchResult != null,
        launchResult,
        scan.installRequests >= 2,
        scan.syncDisabledReason,
        scan.syncFailureReason,
        scan.failureLine);
  }

  /** Everything the output says, collected in one pass and shared by the checks above. */
  private static final class Scan {
    String fullName;
    String id;
    String version;
    String syncDisabledReason;
    String syncFailureReason;
    String failureLine;
    String lastResult;
    boolean syncComplete;
    boolean nothingToSync;
    boolean launchRequested;
    boolean success;
    int installRequests;

    Scan(List<String> lines) {
      lines.stream().map(String::strip).forEach(this::accept);
    }

    private void accept(String line) {
      // The LAST result line is the final verdict: for install-and-launch it is the launch's.
      if (line.startsWith("result::")) {
        lastResult = line.substring("result::".length()).strip();
      }
      switch (line) {
        case String info when info.startsWith(OutputText.INFO_PREFIX) ->
            acceptInfo(info.substring(OutputText.INFO_PREFIX.length()));
        case String field when field.startsWith("actual_dname::") ->
            fullName = OutputText.nonBlank(field.substring("actual_dname::".length())).orElse(null);
        case String field when field.startsWith("actual_id::") ->
            id = OutputText.nonBlank(field.substring("actual_id::".length())).orElse(null);
        case String field when field.startsWith("actual_version::") ->
            version =
                OutputText.nonBlank(field.substring("actual_version::".length())).orElse(null);
        case String verdict when verdict.startsWith("result::success") -> {
          success = true; // the last outcome wins: a retry can follow a failed attempt
          failureLine = null;
        }
        case String error when error.startsWith("Error: ") -> {
          success = false;
          failureLine = error; // more informative than the result::failure line that follows
        }
        case String verdict when verdict.startsWith("result::failure") -> {
          success = false;
          if (failureLine == null) {
            failureLine = verdict;
          }
        }
        default -> {
          // Nothing else is relevant to an install.
        }
      }
    }

    private void acceptInfo(String message) {
      if (message.startsWith(REQUEST_PREFIX)) {
        var command = message.substring(REQUEST_PREFIX.length()).strip();
        if (command.equals("INSTALL") || command.equals("INSTALL_AND_LAUNCH")) {
          installRequests++;
        }
        if (command.equals("LAUNCH") || command.equals("INSTALL_AND_LAUNCH")) {
          launchRequested = true;
        }
      } else if (message.startsWith("File sync complete")) {
        syncComplete = true;
      } else if (message.equals("All files already in sync")) {
        nothingToSync = true;
        syncComplete = true;
      } else if (message.endsWith("sync disabled")) {
        syncDisabledReason = syncDisabledReason == null ? message : syncDisabledReason;
      } else if (message.startsWith("Attempt to use sync failed")
          || message.startsWith("File sync failed")) {
        syncFailureReason = syncFailureReason == null ? message : syncFailureReason;
      } else if (message.startsWith("[ERROR]")) {
        // Printed before a full install when the manifest-only package could not be built.
        syncFailureReason =
            syncFailureReason == null
                ? message.substring("[ERROR]".length()).strip()
                : syncFailureReason;
      }
    }

    InstallResult.Strategy strategy() {
      if (syncComplete) {
        return installRequests > 0 ? InstallResult.Strategy.MINIBAR : InstallResult.Strategy.SYNC;
      }
      return installRequests > 0 ? InstallResult.Strategy.FULL : null;
    }

    /**
     * The launch verdict, if a launch was requested, the install succeeded and the verdict is a
     * number.
     */
    String launchResult() {
      return success
              && launchRequested
              && lastResult != null
              && NUMBER.matcher(lastResult).matches()
          ? lastResult
          : null;
    }
  }
}
