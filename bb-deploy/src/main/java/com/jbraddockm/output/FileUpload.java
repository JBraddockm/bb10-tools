package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * What an upload into an application's sandbox reported.
 *
 * <p>Every component is {@code null} if the tool did not print it.
 *
 * @param fullName the package the file went to: the package name, a dot, and the package id
 * @param path the destination, relative to the sandbox, as the tool received it
 * @param sizeBytes the size of the local file that was sent
 * @param result the text after {@code result::}; {@code success} was observed
 */
public record FileUpload(String fullName, String path, Long sizeBytes, String result)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "fullName", fullName, "path", path, "sizeBytes", sizeBytes, "result", result);
  }
}
