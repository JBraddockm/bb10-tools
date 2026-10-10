package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.List;
import java.util.Map;

/**
 * The devices the Deploy tool found.
 *
 * @param devices the devices in the order the tool listed them
 * @param unparsed lines that did not describe a device, exactly as printed
 */
public record DetectedDevices(List<DetectedDevice> devices, List<String> unparsed)
    implements CommandResult {

  /** Takes immutable copies of the lists. */
  public DetectedDevices {
    devices = List.copyOf(devices);
    unparsed = List.copyOf(unparsed);
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "devices", devices.stream().map(DetectedDevice::toJson).toList(), "unparsed", unparsed);
  }
}
