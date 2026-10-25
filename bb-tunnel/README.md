# bb-tunnel

A pure-Java (JDK 25) client that brings up the BlackBerry **bp2p Wi-Fi tunnel** to a paired
BlackBerry 10 device — no BlackBerry Link/Blend desktop software, no PeerManager daemon, no
BlackBerry ID. The tunnel is a **transport**: once it is up, the device's whole Wi-Fi service surface
is reachable at its inner tunnel address (ULA) — the device-manager CGIs (HTTPS/mTLS on `:8443`),
WebDAV (`:8080`), Blend (`:2708`), discovery (`:80`) — each on its own port. It is not specific to
Blend.

The class-level and `package-info.java` javadoc document the protocol to the byte; the full
investigation and certificate guide live in the companion analysis docs. This README is enough to
build, run, and find your way.

## Status

**Working, live-verified** end to end against a BlackBerry Leap (`C239E313`): the TLS 1.0 control
handshake on `:1111`, the command sequence (INIT / CONNECT / IPV4_INQUIRE / IPV4_DISABLE /
IPV4_ENABLE), the DTLS 1.0 data plane, and a macOS `utun` carrying the device ULA. With the enrolled
client identity and the cert-derived CONNECT ULA the device marks the peer `connected` and routes
return traffic to us, and the device surface (CGIs, WebDAV, Blend) answers over the tunnel.

## Requirements

- **JDK 25** (the `utun` uses the Panama FFM API) on **macOS** (the tun layer is `utun`-specific; see
  the porting notes in `package-info.java`).
- BouncyCastle `bcprov-jdk18on` 1.86, `bctls-jdk18on` 1.86.2, `bcutil-jdk18on` 1.86 — the device speaks
  TLS/DTLS 1.0 with the static-ECDH cipher `0xC005` on P-521 (dropped by OpenSSL 1.1+/3.x) and presents
  a malformed v1-with-extensions certificate that stock JSSE/JCA parsers reject (see `LenientCrypto`).
- A client identity in `src/main/resources/certs/`: `blend_chain.pem` + `blend_key_p8.pem` — the
  enrolled `CN=blend_client_9665` certificate (self-signed EC P-521 CA) and its PKCS#8 key. The common
  name is historical; this one identity is used for the TLS/DTLS mutual auth **and** (via its SHA-1) the
  CONNECT tunnel ULA, and is not Blend-specific. See the analysis docs for creating and enrolling it.

## Build & run

```sh
cd bb-tunnel
mvn -q package      # target/bb-tunnel-1.0-SNAPSHOT.jar (BouncyCastle bundled, Main-Class set)
sudo java --enable-native-access=ALL-UNNAMED -jar target/bb-tunnel-1.0-SNAPSHOT.jar [device-ip]
```

`sudo` is required (creating the `utun` and setting its addresses/routes) and
`--enable-native-access=ALL-UNNAMED` is required for Panama FFM on JDK 25. The demo `main` opens a
tunnel to `192.168.1.118` (override with the first argument), prints the resolved ULAs and how to use
them, and parks until Ctrl-C or the link drops:

```
tunnel up on utun6
  device ULA : fd32:…:bd94
  our ULA    : fd00::…

The device ULA is the device's address on this tunnel; reach its Wi-Fi
services at  https://[<device ULA>]:<port>/  (e.g. :8443 CGIs, :8080 WebDAV, :2708 Blend).
```

## Use as an API

Other modules drive the tunnel through a small handle. You supply only the device IP and the client
identity; the device ULA is read from `CONNECT_RESP` and our ULA is derived from the leaf certificate,
so neither is hardcoded:

```java
try (Tunnel t = Tunnel.open(TunnelConfig.fromClasspath("192.168.1.118"))) {
    String device = t.deviceUla();   // the device's address on the tunnel, from CONNECT_RESP
    String ours   = t.ourUla();      // derived from the client certificate
    // reach any device service over the tunnel at https://[device]:<port>/ — e.g. :8443, :8080, :2708
}
```

`TunnelConfig.fromFiles(host, chainPem, keyPem)` points at PEM files on disk instead of the bundled
identity. The handle keeps the tunnel up on daemon threads until `close()` (try-with-resources).

### Lifecycle

`Tunnel` is a managed handle. It moves through `State { CONNECTING, UP, CLOSED, FAILED }`, and a module
can observe and steer it:

```java
Tunnel t = Tunnel.open(
        TunnelConfig.fromClasspath("192.168.1.118"),
        cause -> log.warn("tunnel down: {}", cause));   // onClosed: null = clean close, else drop cause

t.state();     // CONNECTING | UP | CLOSED | FAILED
t.isOpen();    // true while UP
t.failure();   // the cause of a FAILED drop (else null)
t.restart();   // tear down and bring the same handle back up (e.g. after a drop)
t.close();     // idempotent; AutoCloseable
```

- `open(cfg, onClosed)` registers the listener *before* bring-up, so even an early drop is reported;
  `open(cfg, Duration)` bounds the (slow, P-521) handshake and throws `TimeoutException` if it overruns.
- The inbound thread flips the handle to `FAILED` and fires `onClosed(cause)` if the link dies on its
  own; a user `close()` wins the race (→ `CLOSED`, `onClosed(null)`). The transition is one-shot, so
  `close()` / drop / `restart()` interleave safely.

## Source layout

| File | Role |
|------|------|
| `Tunnel` | The `open()` API handle + `main`. End-to-end bring-up, then the data plane (utun ↔ DTLS, keepalive, NDP); exposes the device/our ULA. |
| `TunnelConfig` | The inputs: device IP + client cert chain/key (`fromClasspath` / `fromFiles`). Everything else is resolved. |
| `TunnelIdentity` | Derives our tunnel ULA from the leaf certificate (`0xfd ‖ prefix ‖ SHA1(prefix ‖ SHA1(certDER))[10:20]`). |
| `TunnelTlsClient` | The BouncyCastle TLS/DTLS 1.0 mutual-auth client: cipher `0xC005`, curve P-521, static-ECDH credential. |
| `LenientCrypto` | Re-encodes the device's malformed v1-with-extensions certificate as v3 so strict parsers accept it. |
| `ControlChannel` | The `[00 cmd len len payload]` command framing; skips async NOTIFY (0x05), throws on ERROR (0x04). |
| `RimPacket` | Builds the 24-byte RimPacket keepalive (PING) reply that holds the data gate open. |
| `TunDevice` | macOS `utun` via Panama FFM (no JNI). Strips/adds the 4-byte AF header. |
| `UdpDatagramTransport` | BouncyCastle `DatagramTransport` over a connected UDP socket (long timeout; ignores stale ICMP). |
| `Hex` | Byte/hex and IPv4/IPv6 string↔bytes helpers, plus the internet checksum. |
| `PemUtil` | Loads DER blocks (cert chain, PKCS#8 key) of a given PEM type from the classpath. |

See `package-info.java` for the full protocol/stack overview, the identity/certificate model, and
porting notes.
