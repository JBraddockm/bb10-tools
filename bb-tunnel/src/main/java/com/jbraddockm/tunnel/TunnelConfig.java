package com.jbraddockm.tunnel;

import java.nio.file.Path;
import java.util.List;

/**
 * The inputs to {@link Tunnel#open}: the device's LAN address and our client identity (a
 * certificate chain plus its private key). Everything else the tunnel needs is <b>resolved at
 * bring-up</b> — the device's tunnel ULA from {@code CONNECT_RESP}, our own ULA from the leaf
 * certificate (see {@link TunnelIdentity}) — so these are the only values a caller supplies.
 *
 * <p>Use {@link #fromClasspath} for the bundled {@code CN=blend_client_9665} identity (the enrolled
 * leaf happens to carry that common name; the tunnel it drives is not Blend-specific), or {@link
 * #fromFiles} to point at PEM files on disk.
 *
 * @param host device LAN IPv4 hosting the TLS control port and answering the DTLS data dial
 * @param controlPort TLS control port (the advertised {@code _tunnel._tcp} port; normally 1111)
 * @param certChainDer our client certificate chain as DER blocks, <b>leaf first</b>; the leaf is
 *     both the mutual-auth identity and (via its SHA-1) the source of our tunnel ULA
 * @param privateKeyDer PKCS#8 DER private key matching the leaf (EC P-521)
 * @param pcIpv4 our inner-IPv4 (169.254/16) advertised in {@code IPV4_ENABLE} and assigned to the
 *     utun
 * @param deviceIpv4 the device's inner-IPv4 (169.254/16) from {@code IPV4_ENABLE}; the device binds
 *     it to {@code bptp0}, so its SMB share is reachable at {@code [deviceIpv4]:445} over the
 *     tunnel
 */
public record TunnelConfig(
    String host,
    int controlPort,
    List<byte[]> certChainDer,
    byte[] privateKeyDer,
    String pcIpv4,
    String deviceIpv4) {

  /** Defaults shared by the factories: control :1111, link-local (169.254/16) inner-IPv4. */
  private static final int CONTROL_PORT = 1111;

  // Inner-IPv4 for the tunnel's IPv4 sub-protocol (IPV4_ENABLE). The device only accepts addresses
  // in
  // 169.254.0.0/16 (its AddressAllocator validates that prefix) and then binds DEVICE_IPV4 to its
  // bptp0
  // interface, so SMB (smbd, bound to bptp0) becomes reachable at [DEVICE_IPV4]:445 over the
  // tunnel.
  // Loopback here is why bptp0 previously fell back to inet 127.0.0.1 and smbd stayed unreachable.
  // Avoids the first/last /24 of the range (RFC 3927-reserved).
  private static final String PC_IPV4 = "169.254.1.2";
  private static final String DEVICE_IPV4 = "169.254.1.1";

  /**
   * The bundled client identity ({@code /certs/blend_chain.pem} + {@code /certs/blend_key_p8.pem})
   * with the default stable inner-IPv4 ({@code 169.254.1.1/.2}).
   */
  public static TunnelConfig fromClasspath(String host) {
    return fromClasspath(host, PC_IPV4, DEVICE_IPV4);
  }

  /**
   * The bundled client identity with an explicit inner-IPv4 pair — e.g. one handed out per session
   * by {@link InnerAddressPool} so several devices can hold concurrent tunnels without colliding.
   */
  public static TunnelConfig fromClasspath(String host, String pcIpv4, String deviceIpv4) {
    try {
      return new TunnelConfig(
          host,
          CONTROL_PORT,
          PemUtil.readBlocks("/certs/blend_chain.pem", "CERTIFICATE"),
          PemUtil.readBlocks("/certs/blend_key_p8.pem", "PRIVATE KEY").getFirst(),
          pcIpv4,
          deviceIpv4);
    } catch (Exception e) {
      throw new IllegalStateException("bundled client identity missing from the classpath", e);
    }
  }

  /**
   * A client identity read from PEM files on disk (cert chain leaf-first + PKCS#8 key) with the
   * default stable inner-IPv4 ({@code 169.254.1.1/.2}).
   */
  public static TunnelConfig fromFiles(String host, Path chainPem, Path keyPem) {
    return fromFiles(host, chainPem, keyPem, PC_IPV4, DEVICE_IPV4);
  }

  /** A client identity read from PEM files on disk with an explicit inner-IPv4 pair. */
  public static TunnelConfig fromFiles(
      String host, Path chainPem, Path keyPem, String pcIpv4, String deviceIpv4) {
    try {
      return new TunnelConfig(
          host,
          CONTROL_PORT,
          PemUtil.readBlocksFromFile(chainPem, "CERTIFICATE"),
          PemUtil.readBlocksFromFile(keyPem, "PRIVATE KEY").getFirst(),
          pcIpv4,
          deviceIpv4);
    } catch (Exception e) {
      throw new IllegalStateException(
          "could not read client identity from " + chainPem + " / " + keyPem, e);
    }
  }

  /** The leaf certificate DER (first chain element) — our bp2p identity. */
  byte[] leafCertDer() {
    return certChainDer.getFirst();
  }
}
