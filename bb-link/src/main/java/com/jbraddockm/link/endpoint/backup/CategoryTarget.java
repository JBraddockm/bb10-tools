package com.jbraddockm.link.endpoint.backup;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where to write a category's archive while it is downloaded. The conversation does no file
 * handling of its own, so the orchestrator hands it a destination per category.
 *
 * @param category the category to download
 * @param destination the file to stream it to
 */
record CategoryTarget(BackupCategory category, Path destination) {

  /** Validates the target. */
  CategoryTarget {
    Objects.requireNonNull(category, "category");
    Objects.requireNonNull(destination, "destination");
  }
}
