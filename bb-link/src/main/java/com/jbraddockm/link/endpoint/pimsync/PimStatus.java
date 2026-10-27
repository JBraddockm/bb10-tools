package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The status that every answer of {@code pimsync.cgi} starts with: {@code
 * {"version":"1.0","status":{"code":N,"msg":"…"},"data":…}}.
 *
 * <p>The answer is not always valid JSON; an error that has no data writes the text unquoted after
 * {@code "data":}. So the answer is first read as JSON and, if that fails, the status is picked out
 * of the text.
 *
 * @param code the status code
 * @param message the status message; may be {@code null}
 */
record PimStatus(int code, String message) {

  private static final Pattern STATUS =
      Pattern.compile(
          "\"status\"\\s*:\\s*\\{\\s*\"code\"\\s*:\\s*(-?\\d+)"
              + "(?:\\s*,\\s*\"msg\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\")?");

  static Optional<PimStatus> read(String body) {
    try {
      if (Json.parse(body) instanceof Map<?, ?> object
          && object.get("status") instanceof Map<?, ?> status
          && status.get("code") instanceof Long code) {
        return Optional.of(
            new PimStatus(code.intValue(), status.get("msg") instanceof String text ? text : null));
      }
      return Optional.empty();
    } catch (Json.JsonException _) {
      return fromText(body);
    }
  }

  private static Optional<PimStatus> fromText(String body) {
    var matcher = STATUS.matcher(body);
    if (!matcher.find()) {
      return Optional.empty();
    }
    var message = matcher.group(2);
    return Optional.of(
        new PimStatus(
            Integer.parseInt(matcher.group(1)),
            message == null ? null : message.replace("\\\"", "\"").replace("\\\\", "\\")));
  }
}
