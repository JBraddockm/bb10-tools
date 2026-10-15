package com.jbraddockm.link.daemon;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.actions.DevicePin;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.InstalledPackage;
import java.util.Map;
import java.util.Optional;

/**
 * The few fields of a device that identify it to a user, returned by {@code connect}.
 *
 * @param name the name the owner gave the device
 * @param hardwareId the hardware identifier
 * @param pin the device PIN, or {@code null} if it could not be read
 * @param serialNumber the board serial number
 * @param osVersion the operating system version
 * @param radioVersion the radio version
 */
record DeviceSummary(
    String name,
    String hardwareId,
    String pin,
    String serialNumber,
    String osVersion,
    String radioVersion) {

  static DeviceSummary of(DeviceProperties properties, Optional<DevicePin> pin) {
    return new DeviceSummary(
        properties.deviceName(),
        properties.hardwareId(),
        pin.map(DevicePin::pin).orElse(null),
        properties.boardSerialNumber(),
        properties.operatingSystem().map(InstalledPackage::version).orElse(null),
        properties.radio().map(InstalledPackage::version).orElse(null));
  }

  Map<String, Object> toJson() {
    return Json.object(
        "name", name,
        "hardwareId", hardwareId,
        "pin", pin,
        "serialNumber", serialNumber,
        "osVersion", osVersion,
        "radioVersion", radioVersion);
  }
}
