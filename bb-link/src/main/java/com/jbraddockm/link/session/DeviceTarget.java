package com.jbraddockm.link.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Objects;

/**
 * Where a session connects and how it proves who it is.
 *
 * @param host the host name or IP address
 * @param port the TCP port
 * @param scheme how to reach the device
 * @param authentication how the session proves who it is
 */
public record DeviceTarget(
    String host, int port, TransportScheme scheme, Authentication authentication) {

  /** The port devices serve their web interface on. */
  public static final int DEFAULT_PORT = 443;

  /**
   * Validates the target.
   *
   * @throws InvalidArgumentException if the host is blank or the port is out of range
   */
  public DeviceTarget {
    Objects.requireNonNull(scheme, "scheme");
    Objects.requireNonNull(authentication, "authentication");
    if (host == null || host.isBlank()) {
      throw new InvalidArgumentException("host", "is required");
    }
    if (port < 1 || port > 65_535) {
      throw new InvalidArgumentException("port", "must be between 1 and 65535");
    }
  }

  /**
   * Builds the URL of a path on the device. An IPv6 address is put in brackets, and its zone stays
   * as written ({@code fe80::1%en0}): that is the form that {@link java.net.URI} reads, which takes
   * a {@code %25} for part of the name of the interface.
   *
   * @param pathAndQuery the path, starting with a slash, and an optional query
   * @return the absolute URL
   */
  String url(String pathAndQuery) {
    var hostPart = host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    return scheme.urlScheme() + "://" + hostPart + ":" + port + pathAndQuery;
  }

  /** Never reveals the password; the location of the key is not shown either. */
  @Override
  public String toString() {
    return "DeviceTarget[host="
        + host
        + ", port="
        + port
        + ", scheme="
        + scheme
        + ", authentication="
        + authentication.type().wireName()
        + "]";
  }
}
