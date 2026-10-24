package com.jbraddockm.tunnel;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Derives a bp2p tunnel ULA from a certificate, the way the device does:
 *
 * <pre>  ULA = 0xfd | prefix[5] | SHA1( prefix[5] || SHA1(certDER) )[10:20]</pre>
 *
 * <p>The 5 prefix bytes are a free choice: the device recomputes the identity from whatever we send
 * in {@code CONNECT} and routes return traffic to it, so {@link #ourUla} uses an all-zero prefix.
 * Verified — with the bundled {@code CN=blend_client_9665} leaf and a zero prefix this reproduces the
 * identity the device accepts ({@code fd00:0000:0000:2fc2:4132:a6e5:2b64:8fdb}).
 *
 * <p>The <i>device's</i> own ULA is deliberately not computed here: its prefix is device-assigned and
 * not cert-derivable, and it is handed to us directly in {@code CONNECT_RESP} — see {@link BlendTunnel}.
 */
final class TunnelIdentity {
  private TunnelIdentity() {}

  /** Our 16-byte tunnel identity for {@code CONNECT}, derived from {@code leafCertDer} with a zero prefix. */
  static byte[] ourUlaBytes(byte[] leafCertDer) {
    return deriveUla(leafCertDer, new byte[5]);
  }

  /** {@link #ourUlaBytes} rendered as a colon IPv6 string. */
  static String ourUla(byte[] leafCertDer) {
    return Hex.ipv6ToString(ourUlaBytes(leafCertDer), 0);
  }

  /** The general derivation {@code 0xfd | prefix5 | SHA1(prefix5 || SHA1(certDER))[10:20]}. */
  static byte[] deriveUla(byte[] certDer, byte[] prefix5) {
    if (prefix5.length != 5) throw new IllegalArgumentException("prefix must be 5 bytes");
    MessageDigest sha1 = sha1();
    byte[] certHash = sha1.digest(certDer);
    sha1.reset();
    sha1.update(prefix5);
    byte[] suffix = sha1.digest(certHash); // SHA1(prefix5 || SHA1(certDER)); take [10:20]

    byte[] ula = new byte[16];
    ula[0] = (byte) 0xfd;
    System.arraycopy(prefix5, 0, ula, 1, 5);
    System.arraycopy(suffix, 10, ula, 6, 10);
    return ula;
  }

  private static MessageDigest sha1() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 unavailable", e); // every JRE ships it
    }
  }
}
