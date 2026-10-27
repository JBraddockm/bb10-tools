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
 * <b>paired-peer identity</b>. So reaching them over Wi-Fi means becoming a registered bp2p peer
 * and talking to the device's tunnel ULA from inside an established tunnel — exactly what this
 * package does. Access requires a certificate the device <b>already trusts</b> — one enrolled by
 * pairing with BlackBerry Link and/or Blend; the certificate alone is not enough.
 *
 * <h2>The wire protocol</h2>
 *
 * The full byte-level protocol is documented in the companion <b>{@code PROTOCOL.md}</b> next to
 * the README: the TLS 1.0 control channel on {@code :1111} (cipher {@code 0xC005}, P-521), the
 * {@code [0x00][cmd][u16 len][payload]} command framing (INIT / CONNECT / IPv4 sub-protocol), the
 * DTLS 1.0 data channel and its 2-byte channel tags, and the certificate-derived identity {@code
 * ULA = 0xfd | prefix[5] | SHA1(prefix[5] || SHA1(certDER))[10:20]}. See also {@code README.md}
 * (build/run/API and how to generate the identity certificate).
 *
 * <h2>Structure &amp; entry points</h2>
 *
 * <ul>
 *   <li>{@link com.jbraddockm.tunnel.TunnelManager} — <b>the entry point</b>: opens and shares one
 *       tunnel per device, ref-counted. It implements {@link com.jbraddockm.tunnel.Tunnels}, the
 *       small service a consumer (the broker) depends on.
 *   <li>{@link com.jbraddockm.tunnel.Cli} — the command line ({@code bb-tunnel [hosts]} demo, or
 *       {@code bb-tunnel serve} for the broker daemon).
 *   <li>Package {@code com.jbraddockm.tunnel.broker} — the broker daemon that serves the tunnel to
 *       bb-link and Blend over a Unix-domain socket.
 *   <li>Package {@code com.jbraddockm.tunnel.internal} — shared helpers (byte/hex/IP and PEM).
 * </ul>
 *
 * <p>Everything else is package-private bring-up machinery: {@code Tunnel} (the end-to-end handle
 * and data plane), {@code TunnelConfig} (inputs), {@code TunnelIdentity} (our ULA from the leaf
 * cert), {@code TunnelTlsClient} and {@code LenientCrypto} (TLS/DTLS 1.0 mutual auth, tolerating
 * the device's malformed certificate), {@code ControlChannel} (command framing), {@code
 * UdpDatagramTransport} (DTLS over UDP), {@code TunDevice} (the macOS utun), and {@code RimPacket}
 * (the keepalive reply).
 */
package com.jbraddockm.tunnel;
