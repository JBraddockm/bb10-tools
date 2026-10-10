package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.List;
import java.util.Map;

/**
 * A device the deploy tool found.
 *
 * @param hostname the device's network name, for example {@code blackberry-0000.lan}
 * @param address the address to connect to, for example {@code 192.168.1.82}
 * @param connection how the device was found; {@code WiFi} has been observed, and a USB connection
 *     is expected to show a different value that has not been captured yet; {@code null} if the
 *     line named none
 * @param unknownFields the fields after the third, whose meaning is not identified (an empty one
 *     was observed)
 */
public record DetectedDevice(
    String hostname, String address, String connection, List<String> unknownFields) {

  /** Takes an immutable copy of the list. */
  public DetectedDevice {
    unknownFields = List.copyOf(unknownFields);
  }

  /**
   * Returns the JSON-compatible representation.
   *
   * @return an insertion-ordered map
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "hostname", hostname,
        "address", address,
        "connection", connection,
        "unknownFields", unknownFields);
  }
}
