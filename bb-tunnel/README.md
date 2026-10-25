# bb-tunnel

A pure-Java (JDK 25) client that brings up the BlackBerry **bp2p Wi-Fi tunnel** to a paired
BlackBerry 10 device — no BlackBerry Link/Blend desktop software, no PeerManager daemon, no
BlackBerry ID. The tunnel is a **transport**: once it is up, the device's whole Wi-Fi service surface
is reachable at its inner tunnel address (ULA) — the device-manager CGIs (HTTPS/mTLS on `:8443`),
WebDAV (`:8080`), Blend (`:2708`), discovery (`:80`) — each on its own port. It is not specific to
Blend.

The wire protocol is documented in [`PROTOCOL.md`](PROTOCOL.md). This README is enough to
build and run the tunnel.

## Device pairing is required

**The tunnel certificate is necessary but not sufficient.** The device completes the tunnel handshake
and routes traffic only for a client certificate it **already trusts** — one enrolled by **pairing the
device with BlackBerry Link and/or Blend**. Pairing is what writes the certificate into the device's
bp2p trust store (`/var/bp2p/trustedpeers` + `certSync.db`); generating the certificate ([below](#identity-certificate))
only creates the identity. An un-enrolled certificate fails the handshake and the device will not route
to it. Services reached *through* the tunnel add their own gates on top (e.g. Blend's password/`authi`
login) — see [`PROTOCOL.md`](PROTOCOL.md) §4.

## Status

**Working, live-verified** end to end against a BlackBerry Leap (`C239E313`): the TLS 1.0 control
handshake on `:1111`, the command sequence (INIT / CONNECT / IPV4_INQUIRE / IPV4_DISABLE /
IPV4_ENABLE), the DTLS 1.0 data plane, and a macOS `utun` carrying the device ULA. With the enrolled
client identity and the cert-derived CONNECT ULA the device marks the peer `connected` and routes
return traffic to us, and the device surface (CGIs, WebDAV, Blend) answers over the tunnel.

## Requirements

- **JDK 25** (the `utun` uses the Panama FFM API) on **macOS** (the tun layer is `utun`-specific; see
  the porting notes in [`PROTOCOL.md`](PROTOCOL.md) §5).
- BouncyCastle `bcprov-jdk18on` 1.86, `bctls-jdk18on` 1.86.2, `bcutil-jdk18on` 1.86 — the device speaks
  TLS/DTLS 1.0 with the static-ECDH cipher `0xC005` on P-521 (dropped by OpenSSL 1.1+/3.x) and presents
  a malformed v1-with-extensions certificate that stock JSSE/JCA parsers reject (see `LenientCrypto`).
- A client identity in `src/main/resources/certs/`: `blend_chain.pem` + `blend_key_p8.pem` — a
  self-signed EC P-521 certificate (`CN=blend_client_9665`; the name is historical, not Blend-specific)
  and its PKCS#8 key. This one identity does the TLS/DTLS mutual auth **and** (via its SHA-1) the CONNECT
  tunnel ULA. See [Identity certificate](#identity-certificate) to generate the files — and note it must
  be enrolled by pairing ([above](#device-pairing-is-required)) to work.

## Identity certificate

Generate the two files bb-tunnel loads (`blend_chain.pem` + `blend_key_p8.pem`) with OpenSSL. They
reproduce the working identity: a self-signed EC **P-521** certificate, **ECDSA-SHA256**, `CA:TRUE`,
`CN=blend_client_9665`, and its key in **PKCS#8**.

```sh
cd bb-tunnel/src/main/resources/certs

# 1. EC P-521 private key
openssl ecparam -name secp521r1 -genkey -noout -out blend_key.pem

# 2. self-signed cert (ECDSA-SHA256, CA:TRUE, SKI/AKI), 10 years, CN=blend_client_9665
openssl req -new -x509 -key blend_key.pem -sha256 -days 3650 \
  -subj "/CN=blend_client_9665" \
  -addext "basicConstraints=critical,CA:TRUE" \
  -addext "subjectKeyIdentifier=hash" \
  -addext "authorityKeyIdentifier=keyid" \
  -out blend_chain.pem

# 3. convert the key to PKCS#8 — the form bb-tunnel loads
openssl pkcs8 -topk8 -nocrypt -in blend_key.pem -out blend_key_p8.pem

# 4. drop the intermediate traditional key
rm blend_key.pem
```

The 5-byte ULA prefix is free (bb-tunnel uses zero), so the tunnel ULA is fully determined by the
certificate — any such P-521 self-signed cert works **as long as the device has it enrolled**.

> ⚠ **This is not enough for access.** These files only create the identity. The device will reject it
> on the handshake until the certificate is **enrolled by pairing** (see
> [Device pairing is required](#device-pairing-is-required)). The cert material is git-ignored and never
> committed (see `src/main/resources/certs/README.md`).

## Build & run

`mvn package` produces **both** a GraalVM native executable and a self-contained fat jar:

```sh
cd bb-tunnel
mvn -q package      # → target/bb-tunnel (native) + target/bb-tunnel-1.0-SNAPSHOT.jar

sudo ./target/bb-tunnel [device-ip]                              # native — the intended way
sudo java -jar target/bb-tunnel-1.0-SNAPSHOT.jar [device-ip]     # or the fat jar (any JDK 25)
```

Building the native image needs a **GraalVM** JDK with `native-image` (this project uses GraalVM CE 25);
on a stock JDK 25, `mvn -DskipNativeBuild=true package` builds just the fat jar. `sudo` is a **run-time**
requirement only — creating the `utun` and
setting its addresses/routes needs root. The native binary takes no JVM flags, so the fat jar's
`--enable-native-access=ALL-UNNAMED` is not needed. The demo `main` opens a tunnel to `192.168.1.118`
(override with the first argument), prints the resolved ULAs and how to use them, and parks until Ctrl-C
or the link drops:

```
tunnel up on utun6
  device ULA : fd32:…:bd94
  our ULA    : fd00::…

The device ULA is the device's address on this tunnel; reach its Wi-Fi
services at  https://[<device ULA>]:<port>/  (e.g. :8443 CGIs, :8080 WebDAV, :2708 Blend).
```

### Native image notes

- **GraalVM for the build only.** `native-image` produces `target/bb-tunnel`; the fat jar is the
  fallback where GraalVM isn't available. The native config lives in this module's `pom.xml` plus the
  parent `pom.xml` (shared `buildArgs`).
- **BouncyCastle.** The low-level `BcTlsCrypto` path (not the JCA provider) needs almost no reflection;
  the build enables the GraalVM **reachability-metadata repository**, which covers the rest — no
  hand-written reflect config. (The *signed*-BC-jar problem is a fat-jar concern, handled by the shade
  signature filter; it does not affect the native image.)
- **Panama FFM (the `utun`).** GraalVM 25 supports the Foreign Function & Memory API, but native image
  does **not** auto-discover downcalls — each must be registered, or the binary throws
  `MissingForeignRegistrationError` on the first `utun` call (after a successful handshake).
  `ForeignRegistrationFeature` (wired via `--features=…`) registers `TunDevice`'s libc downcalls (`TunDevice.DOWNCALLS`,
  its single source of truth) at build time — no reachability-metadata JSON to
  hand-write. The build also passes `--enable-native-access=ALL-UNNAMED` so the binary doesn't print the
  FFM "restricted method" warnings (and keeps working once native access is *enforced* in a future JDK).
  `sudo` (not a build flag) is what the `utun` needs at run time.
- **Embedded certs.** `-H:IncludeResources=certs/.*\.pem` bakes the identity into the binary so
  `fromClasspath()` works like the fat jar. **These are secrets** — this binary is for local use, not
  distribution. For a shippable binary, load the identity at run time via `TunnelConfig.fromFiles(...)`
  and drop that `IncludeResources` arg.
- **macOS only, for now.** Only `TunDevice` (`utun`) and the macOS errno glue are platform-specific (see
  [`PROTOCOL.md`](PROTOCOL.md) §5); the native-build config itself is portable.

## Use as an API

Other modules drive the tunnel through a small handle. You supply only the device IP and the client
identity; the device ULA is read from `CONNECT_RESP` and our ULA is derived from the leaf certificate,
so neither is hardcoded:

```java
try(Tunnel t = Tunnel.open(TunnelConfig.fromClasspath("192.168.1.118"))){
String device = t.deviceUla();   // the device's address on the tunnel, from CONNECT_RESP
String ours = t.ourUla();      // derived from the client certificate
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

t.

state();     // CONNECTING | UP | CLOSED | FAILED
t.

isOpen();    // true while UP
t.

failure();   // the cause of a FAILED drop (else null)
t.

restart();   // tear down and bring the same handle back up (e.g. after a drop)
t.

close();     // idempotent; AutoCloseable
```

- `open(cfg, onClosed)` registers the listener *before* bring-up, so even an early drop is reported;
  `open(cfg, Duration)` bounds the (slow, P-521) handshake and throws `TimeoutException` if it overruns.
- The inbound thread flips the handle to `FAILED` and fires `onClosed(cause)` if the link dies on its
  own; a user `close()` wins the race (→ `CLOSED`, `onClosed(null)`). The transition is one-shot, so
  `close()` / drop / `restart()` interleave safely.

## Source layout

| File                   | Role                                                                                                                                  |
|------------------------|---------------------------------------------------------------------------------------------------------------------------------------|
| `Tunnel`               | The `open()` API handle + `main`. End-to-end bring-up, then the data plane (utun ↔ DTLS, keepalive, NDP); exposes the device/our ULA. |
| `TunnelConfig`         | The inputs: device IP + client cert chain/key (`fromClasspath` / `fromFiles`). Everything else is resolved.                           |
| `TunnelIdentity`       | Derives our tunnel ULA from the leaf certificate (`0xfd ‖ prefix ‖ SHA1(prefix ‖ SHA1(certDER))[10:20]`).                             |
| `TunnelTlsClient`      | The BouncyCastle TLS/DTLS 1.0 mutual-auth client: cipher `0xC005`, curve P-521, static-ECDH credential.                               |
| `LenientCrypto`        | Re-encodes the device's malformed v1-with-extensions certificate as v3 so strict parsers accept it.                                   |
| `ControlChannel`       | The `[00 cmd len len payload]` command framing; skips async NOTIFY (0x05), throws on ERROR (0x04).                                    |
| `RimPacket`            | Builds the 24-byte RimPacket keepalive (PING) reply that holds the data gate open.                                                    |
| `TunDevice`            | macOS `utun` via Panama FFM (no JNI). Strips/adds the 4-byte AF header.                                                               |
| `UdpDatagramTransport` | BouncyCastle `DatagramTransport` over a connected UDP socket (long timeout; ignores stale ICMP).                                      |
| `Hex`                  | Byte/hex and IPv4/IPv6 string↔bytes helpers, plus the internet checksum.                                                              |
| `PemUtil`              | Loads DER blocks (cert chain, PKCS#8 key) of a given PEM type from the classpath.                                                     |

See [`PROTOCOL.md`](PROTOCOL.md) for the full wire protocol — framing, bring-up sequence, ULA
derivation, and porting notes; `package-info.java` carries a short package overview and the class roster.
