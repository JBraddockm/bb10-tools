package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.List;
import java.util.Map;

/**
 * The applications installed on a device.
 *
 * @param apps the applications in the order the device listed them
 * @param unparsed lines that did not describe an application, exactly as printed
 */
public record InstalledApps(List<InstalledApp> apps, List<String> unparsed)
    implements CommandResult {

  /** Takes immutable copies of the lists. */
  public InstalledApps {
    apps = List.copyOf(apps);
    unparsed = List.copyOf(unparsed);
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "apps", apps.stream().map(InstalledApp::toJson).toList(), "unparsed", unparsed);
  }
}
