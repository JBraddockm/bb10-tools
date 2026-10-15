package com.jbraddockm.core.command;

import java.util.LinkedHashMap;
import java.util.Map;

/** The result of a command that succeeds without returning data. */
public enum NoData implements CommandResult {

  /** The only instance. */
  INSTANCE;

  @Override
  public Map<String, Object> toJson() {
    return new LinkedHashMap<>();
  }
}
