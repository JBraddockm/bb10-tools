package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * Whether an app is running.
 *
 * <p>The device answers {@code false} both for an app that is stopped and for one that is not
 * installed.
 *
 * @param running {@code true} or {@code false} as reported, or {@code null} if the device gave no
 *     usable answer
 */
public record AppRunState(Boolean running) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("running", running);
  }
}
