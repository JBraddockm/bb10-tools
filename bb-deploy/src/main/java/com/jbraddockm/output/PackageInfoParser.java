package com.jbraddockm.output;

import java.util.List;

/**
 * Parses the commands that echo the package they acted on: verify and uninstall.
 *
 * <p>Observed on success: {@code actual_dname::}, {@code actual_id::} and {@code actual_version::}
 * filled in, then {@code result::success}. For an app that is not installed the three values are
 * empty and the tool itself fails with {@code result::failure 500 cannot determine package name},
 * so this parser needs no failure rule.
 */
final class PackageInfoParser implements OutputParser<PackageInfo> {

  @Override
  public PackageInfo parse(List<String> lines) {
    var fullName =
        OutputText.lastValueAfter(lines, "actual_dname::")
            .flatMap(OutputText::nonBlank)
            .orElse(null);
    var id =
        OutputText.lastValueAfter(lines, "actual_id::").flatMap(OutputText::nonBlank).orElse(null);
    return new PackageInfo(
        fullName,
        OutputText.packageName(fullName, id).orElse(null),
        id,
        OutputText.lastValueAfter(lines, "actual_version::")
            .flatMap(OutputText::nonBlank)
            .orElse(null),
        OutputText.lastResult(lines).orElse(null));
  }
}
