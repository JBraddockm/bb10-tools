package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import java.util.List;

/**
 * Uploads the categories of a {@code .bbb} back to the device, one form-and-upload conversation on
 * one connection. The device decrypts each category with its backup-and-restore key and applies it,
 * then resets.
 *
 * <p>Destructive: it overwrites the device's data and ends in a reset. It is never replayed after a
 * dropped connection.
 *
 * @param targets the categories to upload, in the order they are sent
 * @param totalSize the total number of bytes to be uploaded across all categories
 */
record RestoreStream(List<UploadTarget> targets, long totalSize)
    implements DeviceCommand<RestoreStreamResult> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if no category is selected or a category appears twice
   */
  RestoreStream {
    targets = List.copyOf(targets);
    if (targets.isEmpty()) {
      throw new InvalidArgumentException("categories", "must name at least one category");
    }
    if (targets.stream().map(UploadTarget::category).distinct().count() != targets.size()) {
      throw new InvalidArgumentException("categories", "names a category more than once");
    }
  }

  /**
   * Prepares the restore of the given targets, with the total taken from their sizes.
   *
   * @param targets the categories to upload
   * @return the command
   */
  static RestoreStream of(List<UploadTarget> targets) {
    return new RestoreStream(targets, targets.stream().mapToLong(UploadTarget::size).sum());
  }

  @Override
  public Conversation<RestoreStreamResult> conversation() {
    return new RestoreStreamConversation(targets, totalSize);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
