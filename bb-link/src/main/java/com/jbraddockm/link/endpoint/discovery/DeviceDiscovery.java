package com.jbraddockm.link.endpoint.discovery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Consumer;

/** Runs a scan and gathers its devices. */
public final class DeviceDiscovery {

  private DeviceDiscovery() {}

  /**
   * Scans for devices.
   *
   * @param scanner how to scan
   * @param onFound told about each device the moment it is found
   * @return all devices found, in the order they were found
   */
  public static DeviceList discover(DeviceScanner scanner, Consumer<DiscoveredDevice> onFound) {
    var devices = Collections.synchronizedList(new ArrayList<DiscoveredDevice>());
    scanner.scan(
        device -> {
          devices.add(device);
          onFound.accept(device);
        });
    synchronized (devices) {
      return new DeviceList(devices);
    }
  }
}
