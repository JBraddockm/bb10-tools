package com.jbraddockm.device;

/**
 * Where a device is and how to log in to it.
 *
 * @param host a host name or an IP address; IPv6 literals may be written with or without brackets
 * @param port the TCP port of the device's HTTPS interface
 * @param password the device password, empty if none is set
 */
public record DeviceTarget(String host, int port, String password) {

  /** The port the device listens on unless configured otherwise. */
  public static final int DEFAULT_PORT = 443;

  private static final int MAX_PORT = 65_535;

  /**
   * Validates the values and strips brackets from an IPv6 host.
   *
   * @throws InvalidArgumentException if the host is blank or the port is out of range
   */
  public DeviceTarget {
    if (host == null || host.isBlank()) {
      throw new InvalidArgumentException("host", "is required");
    }
    host = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    if (port < 1 || port > MAX_PORT) {
      throw new InvalidArgumentException("port", "must be between 1 and " + MAX_PORT);
    }
    password = password == null ? "" : password;
  }

  /**
   * Creates a target on the default port.
   *
   * @param host a host name or an IP address
   * @param password the device password, empty if none is set
   */
  public DeviceTarget(String host, String password) {
    this(host, DEFAULT_PORT, password);
  }

  /**
   * Formats the address as the deploy tool expects it: {@code host:port}, with IPv6 literals in
   * brackets so that the port split is unambiguous.
   *
   * @return the address argument
   */
  String commandLineAddress() {
    return host.indexOf(':') >= 0 ? "[" + host + "]:" + port : host + ":" + port;
  }

  /**
   * Describes the target without revealing the password.
   *
   * @return a text such as {@code DeviceTarget[host=h, port=443, password=***]}
   */
  @Override
  public String toString() {
    return "DeviceTarget[host=" + host + ", port=" + port + ", password=***]";
  }
}
