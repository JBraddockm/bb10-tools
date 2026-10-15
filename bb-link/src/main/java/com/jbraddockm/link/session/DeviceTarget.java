package com.jbraddockm.link.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Objects;

/**
 * Where a session connects and with what password.
 *
 * @param host the host name or IP address
 * @param port the TCP port
 * @param password the device password; empty if the device has none
 * @param scheme how to reach the device
 */
public record DeviceTarget(String host, int port, String password, TransportScheme scheme) {

  /** The port devices serve their web interface on. */
  public static final int DEFAULT_PORT = 443;

  /**
   * Validates the target.
   *
   * @throws InvalidArgumentException if the host is blank or the port is out of range
   */
  public DeviceTarget {
    Objects.requireNonNull(password, "password");
    Objects.requireNonNull(scheme, "scheme");
    if (host == null || host.isBlank()) {
      throw new InvalidArgumentException("host", "is required");
    }
    if (port < 1 || port > 65_535) {
      throw new InvalidArgumentException("port", "must be between 1 and 65535");
    }
  }

  /**
   * Creates a target that is reached over HTTPS.
   *
   * @param host the host name or IP address
   * @param port the TCP port
   * @param password the device password; empty if the device has none
   */
  public DeviceTarget(String host, int port, String password) {
    this(host, port, password, TransportScheme.HTTPS);
  }

  /**
   * Builds the URL of a path on the device.
   *
   * @param pathAndQuery the path, starting with a slash, and an optional query
   * @return the absolute URL
   */
  String url(String pathAndQuery) {
    var hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    return scheme.urlScheme() + "://" + hostPart + ":" + port + pathAndQuery;
  }

  /** Never reveals the password. */
  @Override
  public String toString() {
    return "DeviceTarget[host=" + host + ", port=" + port + ", scheme=" + scheme + "]";
  }
}
