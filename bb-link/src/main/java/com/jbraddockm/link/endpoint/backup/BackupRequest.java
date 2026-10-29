package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * What a backup should do.
 *
 * @param outputFile the {@code .bbb} file to write
 * @param categories the categories to back up, in device order; empty means every category the
 *     device offers
 * @param appPackageTypes the package types to sub-select within the {@code app} category ({@code
 *     system}/{@code data}/{@code bin}); empty means the whole category. Only meaningful when
 *     {@code app} is among the categories (or all are backed up)
 */
public record BackupRequest(
    Path outputFile, List<BackupCategory> categories, List<PackageType> appPackageTypes) {

  /**
   * Takes immutable copies and checks that package types are asked for only with the app category.
   *
   * @throws InvalidArgumentException if package types are given but the app category is not backed
   *     up
   */
  public BackupRequest {
    Objects.requireNonNull(outputFile, "outputFile");
    categories = List.copyOf(categories);
    appPackageTypes = List.copyOf(appPackageTypes);
    if (!appPackageTypes.isEmpty()
        && !categories.isEmpty()
        && !categories.contains(BackupCategory.APP)) {
      throw new InvalidArgumentException(
          "packageTypes",
          "apply to the app category, which is not among the categories to back up");
    }
  }

  /**
   * A backup of whole categories, with no package-type sub-selection.
   *
   * @param outputFile the {@code .bbb} file to write
   * @param categories the categories to back up; empty means all
   * @return the request
   */
  public static BackupRequest of(Path outputFile, List<BackupCategory> categories) {
    return new BackupRequest(outputFile, categories, List.of());
  }
}
