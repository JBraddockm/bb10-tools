package com.jbraddockm.link.endpoint.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What a backup should do.
 *
 * @param outputFile the {@code .bbb} file to write
 * @param categories the categories to back up, in device order; empty means every category the
 *     device offers
 */
public record BackupRequest(Path outputFile, List<BackupCategory> categories) {

  /** Takes an immutable copy of the categories. */
  public BackupRequest {
    Objects.requireNonNull(outputFile, "outputFile");
    categories = List.copyOf(categories);
  }
}
