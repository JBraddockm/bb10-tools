package com.jbraddockm.link.endpoint.backup;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What a restore should do.
 *
 * @param inputFile the {@code .bbb} file to restore from
 * @param categories the categories to restore, in device order; empty means every category in the
 *     archive
 * @param confirm whether the caller has confirmed the destructive restore; without it, nothing is
 *     sent to the device
 */
public record RestoreRequest(Path inputFile, List<BackupCategory> categories, boolean confirm) {

  /** Takes an immutable copy of the categories. */
  public RestoreRequest {
    Objects.requireNonNull(inputFile, "inputFile");
    categories = List.copyOf(categories);
  }
}
