package com.jbraddockm.link.discovery;

import com.qnx.bbt.deploy.Device;
import com.qnx.bbt.deploy.DeviceDetector;
import java.util.function.Consumer;

/** Runs the original tool's {@link DeviceDetector} and converts what it finds. */
final class LibraryDeviceScanner implements DeviceScanner {

  @Override
  public void scan(Consumer<DiscoveredDevice> found) {
    DeviceDetector.getInstance()
        .detectDevices(
            device -> {
              found.accept(convert(device));
              return true;
            });
  }

  private static DiscoveredDevice convert(Device device) {
    var version = device.version == null ? "" : device.version.strip();
    return new DiscoveredDevice(
        device.hostname,
        device.ip,
        ConnectionKind.fromDetectorText(device.getType()),
        version.isEmpty() ? null : version);
  }
}
