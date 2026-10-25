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
 * talking to the device's tunnel ULA from inside an established tunnel — exactly what this package does.
 *
 * <h2>Transport stack (bottom to top)</h2>
 *
 * <ol>
 *   <li><b>TLS 1.0 control channel</b> over TCP to {@code device:1111}, mutual-auth, cipher
 *       {@code 0xC005} (ECDH-ECDSA-AES256-CBC-SHA), curve secp521r1. Carries a tiny command protocol.</li>
 *   <li><b>Command protocol</b> — frames {@code [0x00][cmd][u16 len][payload]}: INIT (0x00/0x01),
 *       CONNECT (0x02/0x03), IPV4_INQUIRE (0x0a/0x0b), IPV4_DISABLE (0x08/0x09), IPV4_ENABLE
 *       (0x06/0x07), async NOTIFY (0x05), ERROR (0x04). CONNECT carries our 16-byte identity ULA and
 *       our UDP port; CONNECT_RESP returns the per-session DTLS data port.</li>
 *   <li><b>DTLS 1.0 data channel</b> over UDP to the per-session port, same mutual-auth cert/cipher.
 *       Afterwards it carries IP packets, each prefixed by a 2-byte channel tag (0x0001 IPv6, 0x0002
 *       control/ping, 0x0003 IPv4).</li>
 *   <li><b>utun</b> on macOS bridges the OS's IP stack to the DTLS channel, so ordinary sockets can
 *       talk to the device's services at {@code [deviceULA]:<port>}.</li>
 * </ol>
 *
 * <h2>Identity &amp; certificates (the crux)</h2>
 *
 * One self-signed EC P-521 certificate (enrolled into the device's bp2p trust via its pairing flow)
 * is used for <i>everything</i>: the TLS and DTLS mutual auth, and — via its SHA-1 — our tunnel
 * identity. That <b>our ULA</b> is derived from the cert ({@link com.jbraddockm.tunnel.TunnelIdentity})
 * as
 * <pre>  ULA = 0xfd | prefix[5] | SHA1( prefix[5] || SHA1(certDER) )[10:20]</pre>
 * and the device verifies it against the same cert before routing return traffic. The 5 prefix bytes
 * are free (we use zero). The <b>device's ULA</b> is not computed — it is handed back in
 * {@code CONNECT_RESP}. So a caller supplies only the device IP and the cert, via
 * {@link com.jbraddockm.tunnel.TunnelConfig}, and {@link com.jbraddockm.tunnel.Tunnel#open}
 * resolves both ULAs. (A full certificate guide — the Link/Blend identities and creation recipes — is in the
 * companion analysis document.)
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
 *
 * <h2>Porting notes</h2>
 *
 * The only platform-specific pieces are {@link com.jbraddockm.tunnel.TunDevice} (macOS utun — swap for
 * a TUN API on another OS) and the TLS/DTLS 1.0 + {@code 0xC005} + P-521 requirement (use a crypto
 * library that still supports them — e.g. mbedTLS, wolfSSL, or OpenSSL 1.0.2 — since OpenSSL 1.1+/3.x
 * dropped {@code 0xC005}; BouncyCastle is used here for that reason). Everything else — the command
 * framing, the CONNECT identity derivation, the channel tags, the keepalive — is plain bytes.
 */
package com.jbraddockm.tunnel;
