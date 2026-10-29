package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

/**
 * What a contact-photo command produced. A successful {@code get} streamed an image to {@link
 * #file} and the binary never passed through a string; a {@code get} of an unchanged photo, and
 * every {@code put}, carries no file and reports the device's status {@link #code} and {@link
 * #message} instead. The image bytes themselves are on disk, not here.
 *
 * @param httpStatus the HTTP status code
 * @param contentType the {@code Content-Type} the device answered with, or {@code null} if absent
 * @param file the file the image was written to, or empty when the device answered with a status
 *     rather than an image
 * @param bytes the number of image bytes written, or {@code 0} when there was no image
 * @param code the device's status code when it answered with one, or {@code null} for a plain image
 * @param message the device's status message, or {@code null}
 */
public record PimPhoto(
    int httpStatus,
    String contentType,
    Optional<Path> file,
    long bytes,
    Integer code,
    String message)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "httpStatus",
        httpStatus,
        "contentType",
        contentType,
        "file",
        file.map(Path::toString).orElse(null),
        "bytes",
        bytes,
        "code",
        code,
        "message",
        message);
  }
}
