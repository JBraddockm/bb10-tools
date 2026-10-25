/**
 * A pure-Java client for the BlackBerry 10 <b>bp2p Wi-Fi tunnel</b>, plus just enough of the data
 * plane to reach a paired device's services over Wi-Fi — with no BlackBerry Link/Blend desktop
 * software, no PeerManager daemon, and no BlackBerry ID. The tunnel is a transport: once it is up,
 * the device's whole Wi-Fi surface is reachable at its tunnel ULA — the device-manager CGIs (HTTPS,
 * mTLS), WebDAV, Blend, discovery — each on its own port. Nothing here is specific to Blend.
 *
 * <h2>Why a tunnel is needed</h2>
 *
 * The device serves almost nothing on its raw LAN IP: its services bind the {@code bptp0}
 * peer-tunnel interface (IPv6 ULA, {@code ipv6only}), and the device authenticates/routes by
 * <b>paired-peer identity</b>. So reaching them over Wi-Fi means becoming a registered bp2p peer and
 * talking to the device's tunnel ULA from inside an established tunnel — exactly what this package
 * does. Access requires a certificate the device <b>already trusts</b> — one enrolled by pairing with
 * BlackBerry Link and/or Blend; the certificate alone is not enough.
 *
 * <h2>The wire protocol</h2>
 *
 * The full byte-level protocol is documented in the companion <b>{@code PROTOCOL.md}</b> next to the
 * README: the TLS 1.0 control channel on {@code :1111} (cipher {@code 0xC005}, P-521), the
 * {@code [0x00][cmd][u16 len][payload]} command framing (INIT / CONNECT / IPv4 sub-protocol), the
 * DTLS 1.0 data channel and its 2-byte channel tags, and the certificate-derived identity
 * {@code ULA = 0xfd | prefix[5] | SHA1(prefix[5] || SHA1(certDER))[10:20]}. See also {@code README.md}
 * (build/run/API and how to generate the identity certificate).
 *
 * <h2>Class roster (port these in order)</h2>
 *
 * <ul>
 *   <li>{@link com.jbraddockm.tunnel.Tunnel} — the {@code open()} API handle and end-to-end
 *       sequence (start here).</li>
 *   <li>{@link com.jbraddockm.tunnel.TunnelConfig} — the inputs (device IP + client certs); the rest
 *       is resolved at bring-up.</li>
 *   <li>{@link com.jbraddockm.tunnel.TunnelIdentity} — derives our ULA from the leaf certificate.</li>
 *   <li>{@link com.jbraddockm.tunnel.TunnelTlsClient} — the TLS/DTLS 1.0 mutual-auth client (0xC005,
 *       P-521, static-ECDH key-agreement client credential).</li>
 *   <li>{@link com.jbraddockm.tunnel.LenientCrypto} — tolerates the device's malformed v1+extensions
 *       certificate (re-encodes it as v3 so strict parsers accept it).</li>
 *   <li>{@link com.jbraddockm.tunnel.ControlChannel} — the {@code [00 cmd len len payload]} framing,
 *       with async-NOTIFY skipping.</li>
 *   <li>{@link com.jbraddockm.tunnel.UdpDatagramTransport} — adapts a connected UDP socket to
 *       BouncyCastle's DTLS, with the device-specific timeout/ICMP handling.</li>
 *   <li>{@link com.jbraddockm.tunnel.TunDevice} — the macOS utun via the Panama FFM API.</li>
 *   <li>{@link com.jbraddockm.tunnel.RimPacket} — the RimPacket keepalive/PING reply.</li>
 *   <li>{@link com.jbraddockm.tunnel.PemUtil}, {@link com.jbraddockm.tunnel.Hex} — small helpers.</li>
 * </ul>
 */
package com.jbraddockm.tunnel;
