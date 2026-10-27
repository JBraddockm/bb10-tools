package com.jbraddockm.link.transport.cgi;

import java.util.Locale;

/**
 * How a device is reached, worked out from the address alone. A device exposes the same management
 * interface over more than one network path, and the path decides which of its programs answer: the
 * USB link and the Wi-Fi tunnel reach the whole interface, a plain Wi-Fi LAN address reaches almost
 * none of it.
 *
 * <p>This is a property of the transport, not of any one command: it names where a connection goes,
 * and knows nothing about what is sent over it.
 */
public enum ConnectionPath {

  /** USB networking: an IPv4 link-local address, {@code 169.254.x.x}. */
  USB,

  /** The Wi-Fi tunnel: an IPv6 unique-local address, {@code fd00::/8} or {@code fc00::/8}. */
  TUNNEL,

  /** A plain Wi-Fi LAN address: a private IPv4 range. */
  LAN,

  /** An address that matches none of the known private ranges. */
  OTHER;

  /**
   * Classifies a host by its address.
   *
   * @param host the host name or IP address a session connects to
   * @return the path; {@link #OTHER} when the address matches none of the known ranges, so that an
   *     ambiguous address is left to the connection itself rather than refused by its shape
   */
  public static ConnectionPath of(String host) {
    var address = normalise(host);
    if (address.startsWith("169.254.")) {
      return USB;
    }
    if (isUniqueLocalIpv6(address)) {
      return TUNNEL;
    }
    if (isPrivateIpv4(address)) {
      return LAN;
    }
    return OTHER;
  }

  /** Strips the brackets of an IPv6 literal and the zone, and lower-cases, for matching. */
  private static String normalise(String host) {
    var address = host.strip();
    if (address.startsWith("[") && address.endsWith("]")) {
      address = address.substring(1, address.length() - 1);
    }
    var zone = address.indexOf('%');
    if (zone >= 0) {
      address = address.substring(0, zone);
    }
    return address.toLowerCase(Locale.ROOT);
  }

  /** An IPv6 unique-local address: the first byte is {@code fc} or {@code fd}. */
  private static boolean isUniqueLocalIpv6(String address) {
    return (address.startsWith("fc") || address.startsWith("fd"))
        && address.length() >= 3
        && (address.charAt(2) == ':' || isHexDigit(address.charAt(2)));
  }

  /** A private IPv4 range: {@code 10/8}, {@code 172.16/12} or {@code 192.168/16}. */
  private static boolean isPrivateIpv4(String address) {
    if (address.startsWith("10.") || address.startsWith("192.168.")) {
      return true;
    }
    if (address.startsWith("172.")) {
      var secondOctet = secondOctetOf(address);
      return secondOctet >= 16 && secondOctet <= 31;
    }
    return false;
  }

  /** The second octet of a dotted IPv4 address, or {@code -1} when it cannot be read. */
  private static int secondOctetOf(String address) {
    var parts = address.split("\\.");
    if (parts.length < 2) {
      return -1;
    }
    try {
      return Integer.parseInt(parts[1]);
    } catch (NumberFormatException _) {
      return -1;
    }
  }

  private static boolean isHexDigit(char character) {
    return (character >= '0' && character <= '9') || (character >= 'a' && character <= 'f');
  }
}
