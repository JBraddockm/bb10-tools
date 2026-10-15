package com.jbraddockm.link.discovery;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.List;
import java.util.Map;

/**
 * The outcome of a scan.
 *
 * @param devices the devices in the order they were found
 */
public record DeviceList(List<DiscoveredDevice> devices) implements CommandResult {

  /** Takes an immutable copy of the devices. */
  public DeviceList {
    devices = List.copyOf(devices);
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object("devices", devices.stream().map(DiscoveredDevice::toJson).toList());
  }
}
