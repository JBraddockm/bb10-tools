package com.jbraddockm.link.endpoint.backup;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One category to upload during a restore: its archive file, extracted from a {@code .bbb}, and its
 * byte count, which the device is told.
 *
 * @param category the category
 * @param file the file holding the category's encrypted archive
 * @param size the number of bytes in the file
 */
record UploadTarget(BackupCategory category, Path file, long size) {

  /** Validates the target. */
  UploadTarget {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(file, "file");
    if (size < 0) {
      throw new IllegalArgumentException("a file cannot have a negative size");
    }
  }
}
