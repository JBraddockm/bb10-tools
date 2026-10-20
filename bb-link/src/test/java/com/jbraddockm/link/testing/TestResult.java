package com.jbraddockm.link.testing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A command result with an arbitrary value, for tests that do not depend on its content.
 *
 * @param value what it holds
 */
public record TestResult(String value) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("value", value);
  }
}
