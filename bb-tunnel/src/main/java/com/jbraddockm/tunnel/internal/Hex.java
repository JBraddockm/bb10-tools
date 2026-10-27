package com.jbraddockm.tunnel.internal;

import java.util.HexFormat;

/**
 * Small byte/hex/IP helpers used throughout the tunnel code. All offsets are into caller-provided
 * arrays.
 */
public final class Hex {
  private Hex() {}

  private static final HexFormat HEX = HexFormat.of(); // lowercase, no separator

  /** Hex string ("2405...") → bytes. */
  public static byte[] fromHex(String s) {
    return HEX.parseHex(s);
  }

  /** Bytes → lowercase hex string. */
  public static String toHex(byte[] b) {
    return HEX.formatHex(b);
  }

  /** Dotted IPv4 string → 4 bytes. */
  public static byte[] ipv4ToBytes(String s) {
    String[] q = s.split("\\.");
    byte[] b = new byte[4];
    for (int i = 0; i < 4; i++) b[i] = (byte) Integer.parseInt(q[i]);
    return b;
  }

  /** 4 bytes at {@code o} → dotted IPv4 string. */
  public static String ipv4ToString(byte[] b, int o) {
    return (b[o] & 0xff)
        + "."
        + (b[o + 1] & 0xff)
        + "."
        + (b[o + 2] & 0xff)
        + "."
        + (b[o + 3] & 0xff);
  }

  /** 16 bytes at {@code o} → colon IPv6 string (no "::" compression). */
  public static String ipv6ToString(byte[] b, int o) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 16; i += 2) {
      if (i > 0) sb.append(':');
      sb.append(String.format("%02x%02x", b[o + i] & 0xff, b[o + i + 1] & 0xff));
    }
    return sb.toString();
  }

  /**
   * The internet 16-bit one's-complement checksum (RFC 1071), used for IPv4, ICMP and the ICMPv6
   * pseudo-header. Sums 16-bit words, folds the carries, and inverts.
   */
  public static int checksum(byte[] b, int off, int len) {
    int s = 0;
    for (int i = 0; i < len; i += 2) {
      int hi = b[off + i] & 0xff, lo = (i + 1 < len) ? (b[off + i + 1] & 0xff) : 0;
      s += (hi << 8) | lo;
    }
    while ((s >> 16) != 0) s = (s & 0xffff) + (s >> 16);
    return (~s) & 0xffff;
  }

  /**
   * Colon IPv6 string → 16 bytes. Handles "::" zero-compression by filling the head from the left
   * and the tail from the right, leaving the gap as zeros. (Used to build the fd00::/8 ULA
   * addresses for bptp0.)
   */
  public static byte[] ipv6ToBytes(String s) {
    String[] halves = s.split("::", -1);
    String[] head = halves[0].isEmpty() ? new String[0] : halves[0].split(":");
    String[] tail =
        (halves.length > 1 && !halves[1].isEmpty()) ? halves[1].split(":") : new String[0];
    byte[] b = new byte[16];
    int i = 0;
    for (String h : head) { // fill from the start
      int v = Integer.parseInt(h, 16);
      b[i++] = (byte) (v >> 8);
      b[i++] = (byte) v;
    }
    int j = 16;
    for (int k = tail.length - 1; k >= 0; k--) { // fill from the end, backwards
      int v = Integer.parseInt(tail[k], 16);
      b[--j] = (byte) v;
      b[--j] = (byte) (v >> 8);
    }
    return b;
  }
}
