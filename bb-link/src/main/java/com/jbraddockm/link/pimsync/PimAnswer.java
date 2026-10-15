package com.jbraddockm.link.pimsync;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.ResponseBody;
import java.util.Map;

/**
 * What {@code pimsync.cgi} answered.
 *
 * <p>The {@code body} is exactly what the device sent: not parsed and not changed, because its
 * shape is not known and part of it is not valid JSON. The code and message are read from it only
 * to tell whether the device accepted the request. The body can contain personal data of the
 * device's owner.
 *
 * @param command the command that was sent
 * @param service the kind of items asked for, or {@code null} for a command that has none
 * @param httpStatus the HTTP status code
 * @param contentType the {@code Content-Type} header, which says whether the body is JSON or
 *     something else, or {@code null} if the device sent none
 * @param code the device's status code, or {@code null} if the answer has none
 * @param message the device's status message, or {@code null}
 * @param body the body, as JSON or as text
 */
public record PimAnswer(
    PimCommand command,
    PimService service,
    int httpStatus,
    String contentType,
    Integer code,
    String message,
    ResponseBody body)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "command",
        command.jsonName(),
        "service",
        service == null ? null : service.wireName(),
        "httpStatus",
        httpStatus,
        "contentType",
        contentType,
        "code",
        code,
        "message",
        message,
        "bodyFormat",
        body.format().wireName(),
        "body",
        body.value());
  }
}
