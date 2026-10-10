package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * The package a verify or uninstall request acted on.
 *
 * @param fullName the package name followed by the package id with each {@code -} written as {@code
 *     .}
 * @param name the package name, or {@code null} if it cannot be derived
 * @param id the package id
 * @param version the package version
 * @param result the text after {@code result::}
 */
public record PackageInfo(String fullName, String name, String id, String version, String result)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "fullName", fullName, "name", name, "id", id, "version", version, "result", result);
  }
}
