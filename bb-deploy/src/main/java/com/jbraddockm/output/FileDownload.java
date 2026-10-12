package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * What a download from an application's sandbox reported.
 *
 * <p>The file either was saved to a local path ({@code savedTo} is set) or its content was printed
 * ({@code content} is set), never both. Every other component is {@code null} if the tool did not
 * print it.
 *
 * <p>The printed content is text: the tool prints it line by line, so line endings are normalised
 * and a final newline cannot be told from its absence. A binary file must be saved to a path.
 *
 * @param fullName the package the file came from: the package name, a dot, and the package id
 * @param path the file, relative to the sandbox, as the tool received it
 * @param sizeBytes the size of the file on the device, as announced before the transfer
 * @param savedTo the local path the file was written to
 * @param content the lines the tool printed after the announcement, joined with {@code \n}
 */
public record FileDownload(
    String fullName, String path, Long sizeBytes, String savedTo, String content)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "fullName", fullName,
        "path", path,
        "sizeBytes", sizeBytes,
        "savedTo", savedTo,
        "content", content);
  }
}
