package com.jbraddockm.daemon;

import com.jbraddockm.DeployError;
import com.jbraddockm.ErrorCode;
import com.jbraddockm.internal.Json;
import java.util.Map;

/** Turns one line of input into a {@link Request}, or into the reason it cannot be one. */
final class RequestDecoder {

  private RequestDecoder() {}

  static Decoding decode(String line) {
    Object parsed;
    try {
      parsed = Json.parse(line);
    } catch (Json.JsonException exception) {
      return rejected(null, "not valid JSON: " + exception.getMessage());
    }
    if (!(parsed instanceof Map<?, ?> object)) {
      return rejected(null, "a request must be a JSON object");
    }
    var id = RequestId.from(object.get("id"));
    if (id.isEmpty()) {
      return rejected(null, "'id' must be an integer or a string");
    }
    if (!(object.get("method") instanceof String method)) {
      return rejected(id.get(), "'method' is required");
    }
    return switch (object.get("params")) {
      case null ->
          new Decoding.Accepted(new Request(id.get(), method, new RequestParams(Map.of())));
      case Map<?, ?> params ->
          new Decoding.Accepted(new Request(id.get(), method, new RequestParams(params)));
      default -> rejected(id.get(), "'params' must be an object");
    };
  }

  private static Decoding.Rejected rejected(RequestId id, String message) {
    return new Decoding.Rejected(id, new DeployError(ErrorCode.INVALID_REQUEST, message));
  }

  /** The outcome of decoding. */
  sealed interface Decoding {

    /** The line is a well-formed request. */
    record Accepted(Request request) implements Decoding {}

    /**
     * The line is not a usable request.
     *
     * @param id the request's id if it could be read, otherwise {@code null}, so the reply carries
     *     a JSON null id
     * @param error why the request was rejected
     */
    record Rejected(RequestId id, DeployError error) implements Decoding {}
  }
}
