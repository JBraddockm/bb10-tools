package com.jbraddockm.link.discovery;

import java.util.function.Consumer;

/** Finds devices. A seam, so tests can run without a network. */
@FunctionalInterface
public interface DeviceScanner {

  /**
   * Scans and reports each device as soon as it is found.
   *
   * @param found receives devices; may be called from several threads
   */
  void scan(Consumer<DiscoveredDevice> found);

  /**
   * Returns the scanner that uses the original tool's detector. It shares the detector's list of
   * known addresses (a file in the user's home directory) with bb-deploy, so a device found by
   * either tool is found faster by the other.
   *
   * @return the scanner
   */
  static DeviceScanner library() {
    return new LibraryDeviceScanner();
  }
}
