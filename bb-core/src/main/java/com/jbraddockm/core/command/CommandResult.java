package com.jbraddockm.core.command;

import java.util.Map;

/** The typed outcome of a command that succeeded. */
public interface CommandResult {

  /**
   * Describes this result as a JSON-compatible tree.
   *
   * <p>The tree consists of insertion-ordered maps, lists, strings, numbers and booleans. Optional
   * fields that have no value are {@code null} and are omitted when the tree is serialised.
   *
   * @return the JSON-compatible view of this result
   */
  Map<String, Object> toJson();
}
