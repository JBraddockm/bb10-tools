package com.jbraddockm.link.daemon;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.characteristics.DeviceCharacteristics;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.InstalledPackage;
import java.util.Map;

/**
 * What {@code connect} tells a client about a device, so it has enough to build a UI or act on the
 * device's state. It merges two sources: {@code dynamicProperties.cgi} (identity, serial, versions,
 * battery) and {@code discovery.cgi} (the PIN — the only source over the tunnel — the GUID, and the
 * capability flags such as the tunnel, developer mode, Blend and the file-share fuse).
 *
 * @param name the name the owner gave the device
 * @param hardwareId the hardware identifier
 * @param pin the device PIN (from {@code discovery.cgi}), or {@code null} if it could not be read
 * @param guid the base64 device GUID (from {@code discovery.cgi}); may be {@code null}
 * @param serialNumber the board serial number
 * @param osVersion the operating system version
 * @param radioVersion the radio version
 * @param freeApplicationSpace the bytes free for applications; may be {@code null}
 * @param hasPower whether the device is on external power; may be {@code null}
 * @param batteryLevel the charge in percent; may be {@code null}
 * @param batteryStateOfHealth the battery's state of health in percent; may be {@code null}
 * @param tunnelUp whether the bp2p tunnel is up; may be {@code null}
 * @param developerMode whether developer mode is on; may be {@code null}
 * @param flashSize the flash size in bytes; may be {@code null}
 * @param sdCard the SD-card state the device reports; may be {@code null}
 * @param modelName the model name; may be {@code null}
 * @param blendPolicy the Blend policy value (a client warns when Blend is off); may be {@code null}
 * @param fuseEnabled whether the file-share fuse is enabled; may be {@code null}
 * @param fuseInstalled whether the file-share fuse is installed; may be {@code null}
 */
record DeviceSummary(
    String name,
    String hardwareId,
    String pin,
    String guid,
    String serialNumber,
    String osVersion,
    String radioVersion,
    Long freeApplicationSpace,
    Boolean hasPower,
    Integer batteryLevel,
    Integer batteryStateOfHealth,
    Boolean tunnelUp,
    Boolean developerMode,
    Long flashSize,
    Integer sdCard,
    String modelName,
    Integer blendPolicy,
    Boolean fuseEnabled,
    Boolean fuseInstalled) {

  /**
   * Builds the summary from the two device reads. The {@code characteristics} may be {@code null}
   * when {@code discovery.cgi} could not be read; the fields it would have supplied are then {@code
   * null} and the rest are as {@code dynamicProperties.cgi} gave them.
   *
   * @param properties what {@code dynamicProperties.cgi} reported
   * @param characteristics what {@code discovery.cgi} reported, or {@code null}
   * @return the summary
   */
  static DeviceSummary of(DeviceProperties properties, DeviceCharacteristics characteristics) {
    return new DeviceSummary(
        properties.deviceName(),
        properties.hardwareId(),
        characteristics != null ? characteristics.pin() : null,
        characteristics != null ? characteristics.guid() : null,
        properties.boardSerialNumber(),
        properties.operatingSystem().map(InstalledPackage::version).orElse(null),
        properties.radio().map(InstalledPackage::version).orElse(null),
        properties.freeApplicationSpace(),
        properties.hasPower(),
        properties.batteryLevel(),
        properties.batteryHealth(),
        characteristics != null ? characteristics.tunnelUp() : null,
        characteristics != null ? characteristics.developerMode() : null,
        characteristics != null ? characteristics.flashSize() : null,
        characteristics != null ? characteristics.sdCard() : null,
        characteristics != null ? characteristics.modelName() : properties.modelName(),
        characteristics != null ? characteristics.blendPolicy() : null,
        characteristics != null ? characteristics.fuseEnabled() : null,
        characteristics != null ? characteristics.fuseInstalled() : null);
  }

  Map<String, Object> toJson() {
    return Json.object(
        "name", name,
        "hardwareId", hardwareId,
        "pin", pin,
        "guid", guid,
        "serialNumber", serialNumber,
        "osVersion", osVersion,
        "radioVersion", radioVersion,
        "freeApplicationSpace", freeApplicationSpace,
        "hasPower", hasPower,
        "batteryLevel", batteryLevel,
        "batteryStateOfHealth", batteryStateOfHealth,
        "tunnelUp", tunnelUp,
        "developerMode", developerMode,
        "flashSize", flashSize,
        "sdCard", sdCard,
        "modelName", modelName,
        "blendPolicy", blendPolicy,
        "fuseEnabled", fuseEnabled,
        "fuseInstalled", fuseInstalled);
  }
}
