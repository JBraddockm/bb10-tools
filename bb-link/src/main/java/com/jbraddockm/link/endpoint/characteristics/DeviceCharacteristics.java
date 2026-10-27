package com.jbraddockm.link.endpoint.characteristics;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * What {@code discovery.cgi} reports about a device, pre-auth and cheaply (no package enumeration).
 * It is the only source of the PIN over the Wi-Fi tunnel, where {@code wipe.cgi} does not answer.
 *
 * @param pin the device PIN as 8-digit uppercase hexadecimal (from the decimal {@code BbPin}); may
 *     be {@code null}
 * @param guid the base64 device GUID; may be {@code null}
 * @param modelName the model name, for example {@code STR100-1}; may be {@code null}
 * @param osType the OS family, for example {@code BlackBerry 10}; may be {@code null}
 * @param platformVersion the OS version, for example {@code 10.3.3.3216}; may be {@code null}
 * @param imei the IMEI; may be {@code null}
 * @param batteryLevel the charge in percent (from {@code Power}); may be {@code null}
 * @param developerMode whether developer mode is on; may be {@code null}
 * @param tunnelUp whether the bp2p tunnel is up (from {@code Bp2pTunnelStatus}); may be {@code
 *     null}
 * @param screenWidth the screen width in pixels; may be {@code null}
 * @param screenHeight the screen height in pixels; may be {@code null}
 * @param flashSize the flash size in bytes; may be {@code null}
 * @param sdCard the SD-card state as the device reports it (for example {@code 0}); may be {@code
 *     null}
 * @param blendPolicy the Blend policy value (for example {@code 1}); may be {@code null}
 * @param fuseEnabled whether the file-share fuse is enabled; may be {@code null}
 * @param fuseInstalled whether the file-share fuse is installed; may be {@code null}
 */
public record DeviceCharacteristics(
    String pin,
    String guid,
    String modelName,
    String osType,
    String platformVersion,
    String imei,
    Integer batteryLevel,
    Boolean developerMode,
    Boolean tunnelUp,
    Integer screenWidth,
    Integer screenHeight,
    Long flashSize,
    Integer sdCard,
    Integer blendPolicy,
    Boolean fuseEnabled,
    Boolean fuseInstalled)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "pin", pin,
        "guid", guid,
        "modelName", modelName,
        "osType", osType,
        "platformVersion", platformVersion,
        "imei", imei,
        "batteryLevel", batteryLevel,
        "developerMode", developerMode,
        "tunnelUp", tunnelUp,
        "screenWidth", screenWidth,
        "screenHeight", screenHeight,
        "flashSize", flashSize,
        "sdCard", sdCard,
        "blendPolicy", blendPolicy,
        "fuseEnabled", fuseEnabled,
        "fuseInstalled", fuseInstalled);
  }
}
