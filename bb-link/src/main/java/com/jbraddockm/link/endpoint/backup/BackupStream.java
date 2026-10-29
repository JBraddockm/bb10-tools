package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Downloads the selected categories from the device into files, one form-driven conversation on one
 * connection: the device prepares the archive, reports when it is ready, then streams each
 * category. The conversation does no file handling of its own; each category is streamed to the
 * destination its {@link CategoryTarget} names.
 *
 * <p>When {@code appPackageTypes} is non-empty the conversation runs the rev2 variant: the
 * {@code mode=} start carries {@code opt=rev2} and the {@code app} data step carries
 * {@code pkgtype}, so only those package types of the app category are streamed. {@code opt=rev2}
 * is not repeated on the activity/data steps (that empties the stream on this firmware). Other
 * categories are unaffected.
 *
 * <p>The command changes nothing on the device, but it is not marked read-only: the cookie-stateful
 * backup session must not be replayed from the start after a dropped connection.
 *
 * @param targets the categories to download and where to write each, in the order they are sent
 * @param appPackageTypes the package types to sub-select within the app category; empty = whole
 * @param pollInterval how long to wait between two questions about whether the archive is ready
 */
record BackupStream(
    List<CategoryTarget> targets, List<PackageType> appPackageTypes, Duration pollInterval)
    implements DeviceCommand<BackupStreamResult> {

  /** How often the device is asked whether the archive is ready. */
  static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(400);

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if no category is selected or a category is selected twice
   * @throws NullPointerException if the poll interval is {@code null}
   */
  BackupStream {
    targets = List.copyOf(targets);
    appPackageTypes = List.copyOf(appPackageTypes);
    Objects.requireNonNull(pollInterval, "pollInterval");
    if (targets.isEmpty()) {
      throw new InvalidArgumentException("categories", "must name at least one category");
    }
    if (targets.stream().map(CategoryTarget::category).distinct().count() != targets.size()) {
      throw new InvalidArgumentException("categories", "names a category more than once");
    }
  }

  /**
   * Prepares the download of the given targets, polling the device every 400 ms while it sizes the
   * archive.
   *
   * @param targets the categories to download and where to write each
   * @param appPackageTypes the package types to sub-select within the app category; empty = whole
   * @return the command
   */
  static BackupStream of(List<CategoryTarget> targets, List<PackageType> appPackageTypes) {
    return new BackupStream(targets, appPackageTypes, DEFAULT_POLL_INTERVAL);
  }

  @Override
  public Conversation<BackupStreamResult> conversation() {
    return new BackupStreamConversation(targets, appPackageTypes, pollInterval);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
