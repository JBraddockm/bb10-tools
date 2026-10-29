package com.jbraddockm.link.endpoint.backup;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One category of a backup, as it was streamed from the device to a file. The bytes are the
 * device's own encrypted archive, kept verbatim.
 *
 * @param category the category
 * @param file the file the category's archive was written to
 * @param bytes the number of bytes written
 */
record DownloadedCategory(BackupCategory category, Path file, long bytes) {

  /** Validates the record. */
  DownloadedCategory {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(file, "file");
  }
}
