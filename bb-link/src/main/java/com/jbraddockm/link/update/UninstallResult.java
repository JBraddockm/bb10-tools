package com.jbraddockm.link.update;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.ResponseBody;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What a removal did. After a failure it is what had been removed until then, and the last thing
 * the device said.
 *
 * @param removed the ids of the packages that the device reported removed, in the order asked
 * @param answer the last answer of the device, as JSON. What the device answers to the end of a
 *     removal has not been seen yet, so whatever it says is passed on whole; it may be empty text.
 */
public record UninstallResult(List<String> removed, ResponseBody answer) implements CommandResult {

  /**
   * Takes an immutable copy of the list.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public UninstallResult {
    removed = List.copyOf(removed);
    Objects.requireNonNull(answer, "answer");
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "removed", removed, "bodyFormat", answer.format().wireName(), "body", answer.value());
  }
}
