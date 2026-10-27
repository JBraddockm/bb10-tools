package com.jbraddockm.tunnel;

/**
 * One tunnel session's inner-IPv4 pair: {@code pcIpv4} is our end (assigned to the utun), {@code
 * deviceIpv4} is the device's end (the device binds it to {@code bptp0}, so its SMB share is
 * reachable at {@code [deviceIpv4]:445}). Both must be link-local ({@code 169.254.0.0/16}) — the
 * device's {@code IPV4_ENABLE} validator accepts that prefix only.
 *
 * @param pcIpv4 our inner IPv4
 * @param deviceIpv4 the device's inner IPv4 (the SMB host)
 */
record InnerIpv4(String pcIpv4, String deviceIpv4) {

  InnerIpv4 {
    requireLinkLocal(pcIpv4);
    requireLinkLocal(deviceIpv4);
  }

  private static void requireLinkLocal(String ipv4) {
    if (!ipv4.startsWith("169.254.")) {
      throw new IllegalArgumentException("inner IPv4 must be link-local 169.254.0.0/16: " + ipv4);
    }
  }
}
