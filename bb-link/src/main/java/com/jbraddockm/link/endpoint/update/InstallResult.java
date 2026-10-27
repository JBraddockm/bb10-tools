package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What an installation did. After a failure it is what had been installed until then, and the last
 * thing the device said.
 *
 * @param installed the packages that the device reported installed, in the order they were sent
 * @param answer the last answer of the device, as JSON: what the research calls {@code UpdateEnd}
 *     on success. Its layout has not been seen on a device yet, so it is passed on whole.
 */
public record InstallResult(List<InstalledPackage> installed, ResponseBody answer)
    implements CommandResult {

  /**
   * Takes an immutable copy of the list.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public InstallResult {
    installed = List.copyOf(installed);
    Objects.requireNonNull(answer, "answer");
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "installed",
        installed.stream().map(entry -> (Object) entry.toJson()).toList(),
        "bodyFormat",
        answer.format().wireName(),
        "body",
        answer.value());
  }
}
