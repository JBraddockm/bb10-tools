package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarValidation;
import com.jbraddockm.core.command.CommandResult;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What {@code inspectPackage} found: what the manifest says, and, if it was asked for, what
 * BlackBerry's rules say about the manifest.
 *
 * @param info what the manifest says about the package
 * @param validation what the rules found; {@code null} if the check was not asked for
 */
record PackageInspection(BarInfo info, BarValidation validation) implements CommandResult {

  /**
   * Validates the components that cannot be absent.
   *
   * @throws NullPointerException if the description is {@code null}
   */
  public PackageInspection {
    Objects.requireNonNull(info, "info");
  }

  @Override
  public Map<String, Object> toJson() {
    var json = new LinkedHashMap<>(info.toJson());
    if (validation != null) {
      json.put("validation", validation.toJson());
    }
    return json;
  }
}
