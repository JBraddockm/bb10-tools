# bb-tunnel

A pure-Java (JDK 25) client that brings up the BlackBerry **bp2p Wi-Fi tunnel** to a paired BlackBerry 10
device and reaches the device's **Blend** surface (HTTPS/WebDAV on port 2708) over Wi-Fi — no BlackBerry
Link/Blend desktop software, no PeerManager daemon, no BlackBerry ID.

The class-level and `package-info.java` javadoc document the protocol to the byte; the full
investigation and certificate guide live in the companion analysis doc (**"BlackBerry Blend — Client
Implementation PRD"** / the tunnel analysis). This README is just enough to build, run, and find your way.

## Status

**Working, live-verified** end to end against a BlackBerry Leap (`C239E313`): TLS 1.0 control handshake on
`:1111`, the full command sequence (INIT / CONNECT / IPV4_INQUIRE / IPV4_DISABLE / IPV4_ENABLE), the DTLS
1.0 data plane, a macOS `utun`, and — with the paired `blend_client_9665` identity and the cert-derived
CONNECT ULA — the device marking the peer `connected`, `auth/broker/login` returning a token, and a
`device/webdav` `PROPFIND` listing the share. The earlier "return traffic dropped" problem is solved: the
CONNECT identity now matches the enrolled cert, so the device routes back to us.

## Requirements

- **JDK 25** (the `utun` uses the Panama FFM API).
- **macOS** (the tun implementation is `utun`-specific; see porting notes in `package-info.java`).
- BouncyCastle `bcprov-jdk18on` 1.86, `bctls-jdk18on` 1.86.2, `bcutil-jdk18on` 1.86 — low-level BouncyCastle
  TLS is required because the device speaks TLS/DTLS 1.0 with the static-ECDH cipher `0xC005` on P-521 (all
  dropped by OpenSSL 1.1+/3.x) and presents a malformed v1-with-extensions certificate that stock JSSE/JCA
  parsers reject (see `LenientCrypto`).
- Client identity in `src/main/resources/certs/`: **`blend_chain.pem`** + **`blend_key_p8.pem`** — the
  enrolled `CN=blend_client_9665` cert (self-signed EC P-521 CA) and its PKCS#8 key. This one identity is
  used for the TLS/DTLS mutual auth **and** (via its SHA-1) the CONNECT tunnel ULA. See the analysis doc's
  certificate section for how to create and enroll it.

## Build & run

```sh
cd bb-tunnel
mvn -q package    # builds target/bb-tunnel-1.0-SNAPSHOT.jar (BouncyCastle bundled, Main-Class set)
sudo java --enable-native-access=ALL-UNNAMED -jar target/bb-tunnel-1.0-SNAPSHOT.jar
```

The shaded jar bundles BouncyCastle, so there is no classpath to assemble. (To run from classes instead:
`mvn -q compile` then `sudo java --enable-native-access=ALL-UNNAMED -cp "target/classes:$(mvn -q
dependency:build-classpath -Dmdep.outputFile=/dev/stdout)" com.jbraddockm.tunnel.BlendTunnel`.)

`sudo` is required (creating the `utun` and setting its addresses/routes); `--enable-native-access=ALL-UNNAMED`
is required for Panama FFM on JDK 25. The program prints `tun up: utunN` once the interface is up, after
which `https://[DEV_ULA]:2708/...` is reachable.

The demo `main` opens a tunnel to `192.168.1.118` (override with `java -jar … <device-ip>`) using the
bundled identity, prints the resolved addresses, and parks.

## Use as an API

Other modules drive the tunnel through a small handle. You supply only the device IP and the client
certs; the device ULA is read from `CONNECT_RESP` and our ULA is derived from the leaf certificate, so
neither is hardcoded:

```java
try(BlendTunnel t = BlendTunnel.open(TunnelConfig.fromClasspath("192.168.1.118"))){
URI blend = t.blendBaseUri();   // https://[fd32:…:bd94]:2708/
String dev = t.deviceUla();     // resolved from CONNECT_RESP
String us = t.ourUla();        // derived from the client cert
// ... reach Blend at `blend` over the tunnel ...
}
```

`TunnelConfig.fromFiles(host, chainPem, keyPem)` points at PEM files on disk instead of the bundled
identity. The handle keeps the tunnel up on daemon threads until `close()` (try-with-resources).

### Lifecycle

`BlendTunnel` is a managed handle, not just open/close. It moves through
`State { CONNECTING, UP, CLOSED, FAILED }`, and a module can observe and steer it:

```java
BlendTunnel t = BlendTunnel.open(
        TunnelConfig.fromClasspath("192.168.1.118"),
        cause -> log.warn("tunnel down: {}", cause));   // onClosed: null = clean close, else the drop cause

t.

state();        // CONNECTING | UP | CLOSED | FAILED
t.

isOpen();       // true while UP
t.

failure();      // the cause of a FAILED drop (else null)
t.

restart();      // tear down and bring the same handle back up (e.g. after a drop)
t.

close();        // idempotent; AutoCloseable
```

- **`open(cfg, onClosed)`** registers the listener *before* bring-up, so even an early drop is reported; **
  `open(cfg, Duration)`** bounds the (slow, P-521) handshake and throws `TimeoutException` if it overruns.
- The inbound thread flips the handle to **`FAILED`** and fires `onClosed(cause)` if the link dies on its
  own; a user `close()` wins the race (→ `CLOSED`, `onClosed(null)`). The transition is one-shot, so
  `close()` / drop / `restart()` are all safe to interleave.

## Source layout

| File                   | Role                                                                                                        |
|------------------------|-------------------------------------------------------------------------------------------------------------|
| `BlendTunnel`          | **The `open()` API handle + Main.** End-to-end bring-up, then the data plane (utun ↔ DTLS, keepalive, NDP). |
| `TunnelConfig`         | The inputs: device IP + client cert chain/key (`fromClasspath` / `fromFiles`). Everything else is resolved. |
| `TunnelIdentity`       | Derives our tunnel ULA from the leaf certificate (`0xfd ‖ prefix ‖ SHA1(prefix ‖ SHA1(certDER))[10:20]`).   |
| `TunnelTlsClient`      | The BouncyCastle TLS/DTLS 1.0 mutual-auth client: cipher `0xC005`, curve P-521, static-ECDH credential.     |
| `LenientCrypto`        | Re-encodes the device's malformed v1-with-extensions certificate as v3 so strict parsers accept it.         |
| `ControlChannel`       | The `[00 cmd len len payload]` command framing; skips async NOTIFY (0x05), throws on ERROR (0x04).          |
| `RimPacket`            | Builds the 24-byte RimPacket keepalive (PING) reply that holds the data gate open.                          |
| `TunDevice`            | macOS `utun` via Panama FFM (no JNI). Strips/adds the 4-byte AF header.                                     |
| `UdpDatagramTransport` | BouncyCastle `DatagramTransport` over a connected UDP socket (long timeout; ignores stale ICMP).            |
| `Hex`                  | Byte/hex and IPv4/IPv6 string↔bytes helpers, plus the internet checksum.                                    |
| `PemUtil`              | Loads DER blocks (cert chain, PKCS#8 key) of a given PEM type from the classpath.                           |

See `package-info.java` for the full protocol/stack overview, the identity/certificate model, and
porting notes (what is macOS- and crypto-library-specific).
