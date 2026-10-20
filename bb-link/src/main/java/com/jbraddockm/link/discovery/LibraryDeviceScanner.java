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
    return convert(device.hostname, device.ip, device.getType(), device.version);
  }

  /**
   * Makes what the daemon says of a device from what the library says of it.
   *
   * @param hostname the name that the device gave itself
   * @param address the address that it was found at
   * @param type the kind of connection, in the words of the library
   * @param version the version of the system that it runs, which may be missing or blank
   * @return the device, with no version when the library gave none
   * @throws IllegalArgumentException if the library names a kind of connection that is not known
   */
  static DiscoveredDevice convert(String hostname, String address, String type, String version) {
    var stripped = version == null ? "" : version.strip();
    return new DiscoveredDevice(
        hostname,
        address,
        ConnectionKind.fromDetectorText(type),
        stripped.isEmpty() ? null : stripped);
  }
}
