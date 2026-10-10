package com.jbraddockm.output;

import java.util.Map;

/** The result of a command whose output carries no structured data. */
public enum NoData implements CommandResult {

  /** The only instance. */
  INSTANCE;

  @Override
  public Map<String, Object> toJson() {
    return Map.of();
  }
}
