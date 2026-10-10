package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * The device's one-word answer to a launch or terminate request.
 *
 * <p>Observed values are a number (a launch that worked, probably the process id), {@code
 * Terminated}, and {@code Invalid} (the device cannot act on the package).
 *
 * @param result the text after {@code result::}, or {@code null} if the device sent none
 */
public record Verdict(String result) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("result", result);
  }
}
