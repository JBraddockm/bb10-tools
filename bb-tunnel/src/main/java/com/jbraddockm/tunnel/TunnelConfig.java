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
 * leaf happens to carry that common name; the tunnel it drives is not Blend-specific), or
 * {@link #fromFiles} to point at PEM files on disk.
 *
 * @param host          device LAN IPv4 hosting the TLS control port and answering the DTLS data dial
 * @param controlPort   TLS control port (the advertised {@code _tunnel._tcp} port; normally 1111)
 * @param certChainDer  our client certificate chain as DER blocks, <b>leaf first</b>; the leaf is both
 *                      the mutual-auth identity and (via its SHA-1) the source of our tunnel ULA
 * @param privateKeyDer PKCS#8 DER private key matching the leaf (EC P-521)
 * @param pcIpv4        our inner-IPv4 advertised in {@code IPV4_ENABLE} (the device routes over IPv6, so it is ignored)
 * @param deviceIpv4    device inner-IPv4 advertised in {@code IPV4_ENABLE} (likewise unused; the IPv6 plane carries traffic)
 */
public record TunnelConfig(
    String host,
    int controlPort,
    List<byte[]> certChainDer,
    byte[] privateKeyDer,
    String pcIpv4,
    String deviceIpv4) {

  /** Defaults shared by the factories: control :1111, loopback inner-IPv4. */
  private static final int CONTROL_PORT = 1111;

  private static final String PC_IPV4 = "127.0.0.2";
  private static final String DEVICE_IPV4 = "127.0.0.1";

  /** The bundled client identity ({@code /certs/blend_chain.pem} + {@code /certs/blend_key_p8.pem}). */
  public static TunnelConfig fromClasspath(String host) {
    try {
      return new TunnelConfig(
          host,
          CONTROL_PORT,
          PemUtil.readBlocks("/certs/blend_chain.pem", "CERTIFICATE"),
          PemUtil.readBlocks("/certs/blend_key_p8.pem", "PRIVATE KEY").getFirst(),
          PC_IPV4,
          DEVICE_IPV4);
    } catch (Exception e) {
      throw new IllegalStateException("bundled client identity missing from the classpath", e);
    }
  }

  /** A client identity read from PEM files on disk: a cert chain (leaf first) and a PKCS#8 key. */
  public static TunnelConfig fromFiles(String host, Path chainPem, Path keyPem) {
    try {
      return new TunnelConfig(
          host,
          CONTROL_PORT,
          PemUtil.readBlocksFromFile(chainPem, "CERTIFICATE"),
          PemUtil.readBlocksFromFile(keyPem, "PRIVATE KEY").getFirst(),
          PC_IPV4,
          DEVICE_IPV4);
    } catch (Exception e) {
      throw new IllegalStateException("could not read client identity from " + chainPem + " / " + keyPem, e);
    }
  }

  /** The leaf certificate DER (first chain element) — our bp2p identity. */
  byte[] leafCertDer() {
    return certChainDer.getFirst();
  }
}
