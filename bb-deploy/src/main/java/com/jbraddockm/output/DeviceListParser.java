package com.jbraddockm.output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Parses {@code -devices}. Observed, with exit status 0, one device found over Wi-Fi:
 *
 * <pre>
 * Info: List Devices
 * blackberry-0000.lan,192.168.1.82,WiFi,
 * Info: END
 * </pre>
 *
 * <p>Each device is a comma-separated line: network name, address, connection type, and a fourth
 * field that was empty. The output is the same whether the device is in Development Mode. How a
 * device connected over USB is listed has not been captured.
 */
final class DeviceListParser implements OutputParser<DetectedDevices> {

  private static final int REQUIRED_FIELDS = 3;
  private static final int FIRST_EXTRA = 3;

  private static Optional<DetectedDevice> parseDevice(String line) {
    var fields = line.strip().split(",", -1);
    if (fields.length < REQUIRED_FIELDS || fields[0].isBlank() || fields[1].isBlank()) {
      return Optional.empty();
    }
    return Optional.of(
        new DetectedDevice(
            fields[0].strip(),
            fields[1].strip(),
            OutputText.nonBlank(fields[2]).orElse(null),
            Arrays.asList(fields).subList(FIRST_EXTRA, fields.length)));
  }

  @Override
  public DetectedDevices parse(List<String> lines) {
    var devices = new ArrayList<DetectedDevice>();
    var unparsed = new ArrayList<String>();
    lines.stream()
        .filter(line -> !OutputText.isNoise(line))
        .forEach(line -> parseDevice(line).ifPresentOrElse(devices::add, () -> unparsed.add(line)));
    return new DetectedDevices(devices, unparsed);
  }
}
