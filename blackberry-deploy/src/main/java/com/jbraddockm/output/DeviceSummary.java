package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Map;

/**
 * The few device properties that identify a device and its model.
 *
 * <p>{@code pin}, {@code serialNumber} and {@code imei} identify one physical device and should be
 * treated as sensitive. Every component is {@code null} if the device does not report it or reports
 * it empty.
 *
 * @param name the unit's network name ({@code hostname}), unique per device
 * @param model the model name, for example {@code Passport}
 * @param modelFullName the full model name, for example {@code BlackBerry Passport}
 * @param modelNumber the model number
 * @param hardwareId the hardware id, which identifies the hardware model, not the unit
 * @param pin the device PIN
 * @param serialNumber the serial number
 * @param imei the IMEI
 * @param os the operating system name
 * @param osVersion the software bundle version, believed to be the operating system version
 */
public record DeviceSummary(
    String name,
    String model,
    String modelFullName,
    String modelNumber,
    String hardwareId,
    String pin,
    String serialNumber,
    String imei,
    String os,
    String osVersion) {

  /**
   * Extracts the summary from the full device information.
   *
   * @param info the parsed output of {@code -listDeviceInfo}
   * @return the summary
   */
  public static DeviceSummary of(DeviceInfo info) {
    return new DeviceSummary(
        info.find("hostname").orElse(null),
        info.find("modelname").orElse(null),
        info.find("modelfullname").orElse(null),
        info.find("modelnumber").orElse(null),
        info.find("hardwareid").orElse(null),
        info.find("devicepin").orElse(null),
        info.find("deviceserialnumber").orElse(null),
        info.find("IMEI").orElse(null),
        info.find("device_os").orElse(null),
        info.find("scmbundle").orElse(null));
  }

  /**
   * Returns the JSON-compatible representation, omitting what the device did not report.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "name", name,
        "model", model,
        "modelFullName", modelFullName,
        "modelNumber", modelNumber,
        "hardwareId", hardwareId,
        "pin", pin,
        "serialNumber", serialNumber,
        "imei", imei,
        "os", os,
        "osVersion", osVersion);
  }
}
