package com.jbraddockm.link.discovery;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A device found by a scan.
 *
 * @param hostname the name the detector reports. For a device without a password it is the
 *     operating system name and version instead of the network name.
 * @param address the IP address
 * @param connection how the device is connected
 * @param version the operating system version, if the device allowed reading it; otherwise {@code
 *     null}
 */
public record DiscoveredDevice(
    String hostname, String address, ConnectionKind connection, String version) {

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map; an unknown version is {@code null}
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "hostname", hostname,
        "address", address,
        "connection", connection.wireName(),
        "version", version);
  }
}
