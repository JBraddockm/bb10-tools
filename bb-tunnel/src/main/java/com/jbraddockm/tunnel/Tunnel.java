package com.jbraddockm.tunnel;

import java.net.*;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * A live BlackBerry <b>bp2p Wi-Fi tunnel</b> to a paired BB10 device, as a small API other modules
 * can drive: give {@link #open} a {@link TunnelConfig} (device IP + client certs) and it returns a
 * handle that exposes the device's inner tunnel address ({@link #deviceUla()}). For the handle's
 * lifetime the device's whole Wi-Fi service surface is reachable at that ULA — the device-manager
 * CGIs (HTTPS, mTLS), WebDAV, Blend, discovery — each on its own port. The tunnel is a transport;
 * it is not specific to Blend.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>A handle moves through {@link State}: {@code CONNECTING} → {@code UP} once bring-up finishes,
 * then either {@code CLOSED} (via {@link #close}) or {@code FAILED} (the link dropped on its own).
 * Callers can:
 *
 * <ul>
 *   <li>{@link #open(TunnelConfig)} — bring up, blocking; or {@link #open(TunnelConfig, Duration)}
 *       to bound the (slow, P-521) handshake and abort if it overruns.
 *   <li>{@link #state()} / {@link #isOpen()} / {@link #failure()} — observe liveness.
 *   <li>{@link #onClosed(Consumer)} — be told once when the tunnel leaves {@code UP}: {@code null}
 *       for a clean close, or the cause for an unexpected drop (so a module can reconnect).
 *   <li>{@link #restart()} — tear down and bring the same handle back up (e.g. after a drop).
 *   <li>{@link #close()} — tear everything down (idempotent; {@code AutoCloseable}).
 * </ul>
 *
 * <h2>What {@code open} resolves (so callers don't hardcode it)</h2>
 *
 * <ul>
 *   <li><b>Our ULA</b> — the 16-byte identity we send in {@code CONNECT}, derived from the config's
 *       leaf certificate by {@link TunnelIdentity}. The device recomputes it from our trusted peer
 *       cert and routes return traffic to it, so it must match the cert we present.
 *   <li><b>Device ULA</b> — the device's inner tunnel address, read straight out of {@code
 *       CONNECT_RESP} (its first 16 bytes); stable across sessions. The device binds its services
 *       to its {@code bptp0} tunnel interface, so each is reached at {@code [deviceUla]:<port>}.
 * </ul>
 *
 * <h2>Bring-up sequence (what a port must reproduce)</h2>
 *
 * <ol>
 *   <li><b>Control channel</b> — TCP to {@code host:controlPort} + a <b>TLS 1.0 mutual-auth</b>
 *       handshake (cipher {@code 0xC005}, curve secp521r1) presenting the config's cert. See {@link
 *       TunnelTlsClient}.
 *   <li><b>INIT</b> — command {@code 0x00}, fixed 8-byte body {@code 24 05 20 03 00 01 00 00}; the
 *       device echoes it as {@code INIT_RESP} (0x01).
 *   <li><b>CONNECT</b> — command {@code 0x02}, 20-byte body {@code [16B our ULA][u16 our UDP port]
 *       [00 01]}. {@code CONNECT_RESP} (0x03) returns {@code [16B device ULA][u16 DTLS port][u16
 *       flags]}: the device ULA and the per-session DTLS data port are read here.
 *   <li><b>IPv4 sub-protocol</b> — {@code IPV4_INQUIRE} (0x0a) → {@code 0x0b}, then {@code
 *       IPV4_DISABLE} (0x08) and {@code IPV4_ENABLE} (0x06) carrying our/device inner IPv4.
 *   <li><b>Data channel</b> — dial UDP to {@code host:<DTLS port>} and run a <b>DTLS 1.0
 *       mutual-auth</b> handshake. Afterwards it carries IP packets, each prefixed by a 2-byte
 *       channel tag (0x0001 IPv6, 0x0002 control/ping, 0x0003 IPv4).
 *   <li><b>Use</b> — a macOS utun ({@link TunDevice}) is created and the device's ULA routed into
 *       it; three daemon threads then run the data plane (see {@link #startDataPlane}).
 * </ol>
 *
 * <p>Command framing is {@code [0x00][cmd][u16 len][payload]} on the TLS stream — see {@link
 * ControlChannel}. <b>Runtime:</b> requires {@code sudo} (creating a utun is privileged) and, on
 * JDK&nbsp;25, {@code --enable-native-access=ALL-UNNAMED} (the utun uses the Panama FFM API).
 */
public final class Tunnel implements AutoCloseable {

  private final TunnelConfig cfg;
  // Our identity, derived from the config's leaf cert up front.
  private final byte[] ourUlaBytes;
  private final String ourUla;
  // Lifecycle state. `lifecycle` guards the one-shot transition in stop().
  private final Object lifecycle = new Object();
  // The device's identity, resolved from CONNECT_RESP during bring-up.
  private volatile String deviceUla;
  private byte[] deviceUlaBytes;

  // Transport + interface, retained so teardown() can close them.
  private Socket controlSocket;
  private TlsClientProtocol controlProto;
  private DatagramSocket udp;
  private DTLSTransport dtls;
  private TunDevice tun;
  private volatile boolean running;
  private volatile State state = State.CONNECTING;
  private volatile Throwable failure;
  private volatile Consumer<Throwable> onClosed;
  /**
   * Package-private: builds a handle in {@link State#CONNECTING} without bringing the tunnel up.
   * {@link #open} uses it and then calls {@link #bringUp}; tests use it to exercise lifecycle
   * transitions without a device. The {@code ourUla} is derived here from the config's leaf cert.
   */
  Tunnel(TunnelConfig cfg) {
    this.cfg = cfg;
    this.ourUlaBytes = TunnelIdentity.ourUlaBytes(cfg.leafCertDer());
    this.ourUla = Hex.ipv6ToString(ourUlaBytes, 0);
  }

  /**
   * Brings the tunnel up end to end (control handshake → DTLS data tunnel → utun + data-plane
   * threads) and returns once the interface is live. The returned handle keeps the tunnel open on
   * daemon threads until {@link #close} (or an unexpected drop).
   */
  public static Tunnel open(TunnelConfig cfg) throws Throwable {
    return open(cfg, (Consumer<Throwable>) null);
  }

  /**
   * As {@link #open(TunnelConfig)}, registering {@code onClosed} <i>before</i> bring-up so even an
   * early drop is reported.
   */
  public static Tunnel open(TunnelConfig cfg, Consumer<Throwable> onClosed) throws Throwable {
    Tunnel t = new Tunnel(cfg);
    t.onClosed = onClosed;
    try {
      t.bringUp();
    } catch (Throwable e) {
      t.state = State.FAILED;
      t.failure = e;
      t.teardown(); // close whatever partial transports were opened
      throw e;
    }
    return t;
  }

  /**
   * As {@link #open(TunnelConfig)}, but bounds the bring-up: if it does not reach {@link State#UP}
   * within {@code timeout}, the partial transports are closed and a {@link TimeoutException} is
   * thrown. (The DTLS handshake alone can take minutes on 2013 hardware, so pick generously.)
   */
  public static Tunnel open(TunnelConfig cfg, Duration timeout) throws Throwable {
    Tunnel t = new Tunnel(cfg);
    CountDownLatch done = new CountDownLatch(1);
    AtomicReference<Throwable> err = new AtomicReference<>();
    Thread worker =
        new Thread(
            () -> {
              try {
                t.bringUp();
              } catch (Throwable e) {
                err.set(e);
              } finally {
                done.countDown();
              }
            },
            "bringup");
    worker.setDaemon(true);
    worker.start();

    if (!done.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
      t.state = State.FAILED;
      t.teardown(); // unblock the worker's blocking handshake I/O
      throw new TimeoutException("tunnel bring-up exceeded " + timeout);
    }
    if (err.get() != null) {
      t.state = State.FAILED;
      t.failure = err.get();
      t.teardown();
      throw err.get();
    }
    return t;
  }

  /**
   * Runs a local command (ifconfig/route), inheriting stdio; logs a non-zero exit but never throws.
   */
  private static void runCmd(String... args) {
    try {
      int rc = new ProcessBuilder(args).inheritIO().start().waitFor();
      if (rc != 0) System.out.println("cmd rc=" + rc + ": " + String.join(" ", args));
    } catch (Throwable t) {
      System.out.println("cmd failed: " + String.join(" ", args) + " : " + t);
    }
  }

  /**
   * Demo entry point: open a tunnel to the device at {@code args[0]} (default {@code
   * 192.168.1.118}) with the bundled client identity, print the resolved ULAs and how to use them,
   * and park until interrupted or the link drops.
   */
  static void main(String[] args) throws Throwable {
    String host = args.length > 0 ? args[0] : "192.168.1.118";
    CountDownLatch stopped = new CountDownLatch(1);
    try (Tunnel t =
        Tunnel.open(
            TunnelConfig.fromClasspath(host),
            cause -> {
              System.out.println(cause == null ? "tunnel closed" : "tunnel dropped: " + cause);
              stopped.countDown();
            })) {
      System.out.println();
      System.out.println("tunnel up on " + t.tunInterface());
      System.out.println("  device ULA : " + t.deviceUla());
      System.out.println("  our ULA    : " + t.ourUla());
      System.out.println();
      System.out.println("The device ULA is the device's address on this tunnel; reach its Wi-Fi");
      System.out.println(
          "services at  https://[<device ULA>]:<port>/  (e.g. :8443 CGIs, :8080 WebDAV, :2708 Blend).");
      System.out.println();
      System.out.println("  device IPv4: " + t.deviceIpv4() + " (inner, for SMB)");
      System.out.println(
          "The device's SMB share is at  smb://" + t.deviceIpv4() + "/  (mount with the");
      System.out.println("login.cgi 'dtm' credentials) — the Link network drive over Wi-Fi.");
      System.out.println("Ctrl-C to stop.");
      stopped.await(); // park until the link drops; the daemon threads carry the tunnel
    }
  }

  /**
   * The device's inner tunnel ULA, resolved from {@code CONNECT_RESP} (e.g. {@code fd32:…:bd94}).
   */
  public String deviceUla() {
    return deviceUla;
  }

  /**
   * The device's inner IPv4 on the tunnel (link-local {@code 169.254.x.y}), advertised in {@code
   * IPV4_ENABLE} and bound by the device to its {@code bptp0} interface. Its SMB share is reachable
   * at {@code [deviceIpv4]:445} once the tunnel is {@link State#UP}. Static (from the config), so
   * it is also valid before bring-up.
   */
  public String deviceIpv4() {
    return cfg.deviceIpv4();
  }

  /**
   * Our inner IPv4 on the tunnel ({@code 169.254.x.y}), assigned to the utun as the SMB client
   * address.
   */
  public String pcIpv4() {
    return cfg.pcIpv4();
  }

  /** Our tunnel ULA, derived from the config's leaf certificate. */
  public String ourUla() {
    return ourUla;
  }

  /** The device LAN host this tunnel is bound to. */
  public String host() {
    return cfg.host();
  }

  /** The utun interface name (e.g. {@code utun5}), or {@code null} before the data plane is up. */
  public String tunInterface() {
    return tun == null ? null : tun.name();
  }

  /** The current lifecycle state. */
  public State state() {
    return state;
  }

  /** True while the data plane is live ({@link State#UP}). */
  public boolean isOpen() {
    return state == State.UP;
  }

  /** The cause of a {@link State#FAILED} drop (or a failed bring-up), else {@code null}. */
  public Throwable failure() {
    return failure;
  }

  /**
   * Registers a listener fired exactly once when the tunnel leaves {@link State#UP}: {@code null}
   * on a clean {@link #close}, or the cause on an unexpected drop. Returns {@code this} for
   * chaining. Prefer {@link #open(TunnelConfig, Consumer)} to avoid missing a drop between {@code
   * open} returning and registration.
   */
  public Tunnel onClosed(Consumer<Throwable> listener) {
    this.onClosed = listener;
    return this;
  }

  /**
   * Tears the current tunnel down and brings the <i>same</i> handle back up (fresh control + DTLS
   * handshake and a new utun). Useful after a {@link State#FAILED} drop. The interim stop does not
   * fire {@link #onClosed}. Blocks for the full bring-up, like {@link #open}.
   */
  public synchronized void restart() throws Throwable {
    stop(State.CLOSED, null, false); // quiet teardown — a restart is not a "closed" event
    state = State.CONNECTING;
    failure = null;
    bringUp();
  }

  // ---------------------------------------------------------------------------------------------

  /**
   * Tears the tunnel down: stops the data-plane threads, closes the transports, fires {@link
   * #onClosed}(null).
   */
  @Override
  public void close() {
    stop(State.CLOSED, null, true);
  }

  private void bringUp() throws Throwable {
    BcTlsCrypto crypto = new LenientCrypto(new SecureRandom());

    // ---------- control plane: TLS 1.0 over TCP :controlPort ----------
    controlSocket = new Socket(cfg.host(), cfg.controlPort());
    controlProto =
        new TlsClientProtocol(controlSocket.getInputStream(), controlSocket.getOutputStream());
    controlProto.connect(
        new TunnelTlsClient(crypto, false, cfg.certChainDer(), cfg.privateKeyDer()));
    System.out.println("HANDSHAKE OK");
    ControlChannel ctl =
        new ControlChannel(controlProto.getOutputStream(), controlProto.getInputStream());

    ctl.send(0x00, Hex.fromHex("2405200300010000")); // INIT (fixed version/capability token)
    System.out.println("INIT_RESP  body=" + Hex.toHex(ctl.read(0x01).body));

    // CONNECT: bind our UDP data socket, advertise its port + our identity ULA; CONNECT_RESP then
    // hands back the device's own ULA and the per-session DTLS port.
    udp = new DatagramSocket(0);
    int hostUdpPort = udp.getLocalPort();
    System.out.println("host UDP port = " + hostUdpPort);
    byte[] connectBody = new byte[20]; // [16B our ULA][u16 UDP port][00 01]
    System.arraycopy(ourUlaBytes, 0, connectBody, 0, 16);
    connectBody[16] = (byte) (hostUdpPort >> 8); // u16 UDP data port (htons)
    connectBody[17] = (byte) hostUdpPort;
    connectBody[18] = 0x00; // flags trailer: host sends 00 01
    connectBody[19] = 0x01;
    ctl.send(0x02, connectBody);
    ControlChannel.Frame connResp = ctl.read(0x03);
    byte[] b = connResp.body;
    deviceUlaBytes =
        Arrays.copyOfRange(b, 0, 16); // CONNECT_RESP body[0:16] = device inner tunnel ULA
    deviceUla = Hex.ipv6ToString(deviceUlaBytes, 0);
    int dtlsPort = ((b[16] & 0xff) << 8) | (b[17] & 0xff); // per-session DTLS port = body[16:18]
    System.out.println(
        "CONNECT_RESP device ULA="
            + deviceUla
            + " DTLS port="
            + dtlsPort
            + " flags=0x"
            + String.format("%04x", ((b[18] & 0xff) << 8) | (b[19] & 0xff)));

    // IPv4 sub-protocol: inquire restrictions, clear stale state, then enable our inner IPv4.
    ctl.send(0x0a, new byte[] {0x00}); // IPV4_INQUIRE
    ControlChannel.parseIpv4Restrictions(ctl.read(0x0b).body);

    ctl.send(0x08, new byte[] {0x00}); // IPV4_DISABLE (clear stale)
    try {
      ctl.read(0x09);
    } catch (Exception ignore) {
    }
    byte[] enableBody = new byte[8]; // IPV4_ENABLE: pcIPv4 (4) + remoteIPv4 (4)
    System.arraycopy(Hex.ipv4ToBytes(cfg.pcIpv4()), 0, enableBody, 0, 4);
    System.arraycopy(Hex.ipv4ToBytes(cfg.deviceIpv4()), 0, enableBody, 4, 4);
    ctl.send(0x06, enableBody);
    ControlChannel.Frame en = ctl.read(0x07);
    int enStatus =
        ((en.body[0] & 0xff) << 24)
            | ((en.body[1] & 0xff) << 16)
            | ((en.body[2] & 0xff) << 8)
            | (en.body[3] & 0xff);
    System.out.println(
        "IPV4_ENABLE_RESP status=0x"
            + String.format("%08x", enStatus)
            + (enStatus == 0 ? "  (configured)" : "  (conflict, proceeding)"));

    // ---------- data plane: DTLS 1.0 to the per-session port ----------
    System.out.println("  dialing DTLS...");
    udp.connect(InetAddress.getByName(cfg.host()), dtlsPort);
    DatagramTransport transport = new UdpDatagramTransport(udp, 1500);
    long t0 = System.currentTimeMillis();
    dtls =
        new DTLSClientProtocol()
            .connect(
                new TunnelTlsClient(crypto, true, cfg.certChainDer(), cfg.privateKeyDer()),
                transport);
    System.out.println("DTLS HANDSHAKE OK (" + (System.currentTimeMillis() - t0) + "ms)");

    startDataPlane();
  }

  /**
   * Stands up a macOS utun, gives the interface our ULA and inner IPv4, routes the device's ULA and
   * inner IPv4 into it, starts the three data-plane daemon threads, and marks the handle {@link
   * State#UP}.
   *
   * <p><b>Both planes.</b> IPv6 (the device's main service surface on {@code bptp0}) and inner IPv4
   * ({@code 169.254/16}, for the SMB network drive — the device binds {@code deviceIpv4} to {@code
   * bptp0} in the {@code IPV4_ENABLE} step, so {@code [deviceIpv4]:445} is reachable through the
   * tunnel). The data plane already carries both (channel tags {@code 0x0001}/{@code 0x0003}); only
   * the interface addressing differs.
   */
  private void startDataPlane() throws Throwable {
    tun = new TunDevice();
    System.out.println("tun up: " + tun.name());

    runCmd("ifconfig", tun.name(), "inet6", "fe80::2", "prefixlen", "64");
    runCmd("ifconfig", tun.name(), "mtu", "1356");
    runCmd("ifconfig", tun.name(), "inet6", ourUla, "prefixlen", "8");
    runCmd("route", "-n", "add", "-inet6", deviceUla, "-interface", tun.name());

    // Inner IPv4 plane (SMB / Link network drive): give the utun our inner IPv4 as a point-to-point
    // link to the device's inner IPv4 (which installs a host route to it), so macOS can reach
    // [deviceIpv4]:445 — the device bound deviceIpv4 to bptp0 in the IPV4_ENABLE step above.
    runCmd(
        "ifconfig",
        tun.name(),
        "inet",
        cfg.pcIpv4(),
        cfg.deviceIpv4(),
        "netmask",
        "255.255.255.255");
    runCmd("route", "-n", "add", "-inet", cfg.deviceIpv4(), "-interface", tun.name());

    running = true;
    startTunToDtls();
    startNeighbourAdvertiser();
    startDtlsToTun();
    state = State.UP;
  }

  /**
   * Inbound pump (daemon): reads DTLS datagrams, answers keepalive PINGs (channel {@code 0x0002},
   * type {@code 0x0001}) to hold the data gate open, and writes inbound IP packets (channels {@code
   * 0x0001}/{@code 0x0003}) to the tun. A read error while still {@code running} is treated as a
   * dropped link and transitions the handle to {@link State#FAILED}.
   */
  private void startDtlsToTun() {
    Thread t =
        new Thread(
            () -> {
              byte[] rx = new byte[4096];
              while (running) {
                int r;
                try {
                  r = dtls.receive(rx, 0, rx.length, 5000);
                } catch (SocketException e) {
                  if (running) stop(State.FAILED, e, true); // link dropped (not our own close)
                  break;
                } catch (Exception e) {
                  if (running) System.out.println("recv ex: " + e);
                  continue;
                }
                if (r <= 0) continue;
                int tag = ((rx[0] & 0xff) << 8) | (rx[1] & 0xff);
                switch (tag) {
                  case 0x0002 -> { // RimPacket control: answer the PING (type 0x0001)
                    if ((((rx[2] & 0xff) << 8) | (rx[3] & 0xff)) == 0x0001) {
                      try {
                        byte[] reply = RimPacket.keepAliveReply(Arrays.copyOf(rx, r));
                        dtls.send(reply, 0, reply.length);
                      } catch (Throwable ignore) {
                      }
                    }
                  }
                  case 0x0001,
                      0x0003 -> { // an inbound IP packet (v6 service traffic / v4 SMB) -> tun
                    try {
                      tun.write(rx, 2, r - 2);
                    } catch (Throwable ignore) {
                    }
                  }
                  default -> {} // unknown tag: ignore
                }
              }
            },
            "dtls->tun");
    t.setDaemon(true);
    t.start();
  }

  /**
   * Outbound pump (daemon): reads IP packets off the tun, tags them by IP version ({@code 0x0001}
   * for IPv6, {@code 0x0003} for IPv4), and writes them to the DTLS channel. Link-local multicast
   * is dropped (only noise on the point-to-point tunnel).
   */
  private void startTunToDtls() {
    Thread t =
        new Thread(
            () -> {
              try {
                while (running) {
                  byte[] ip = tun.read();
                  if (ip.length == 0) continue;
                  if ((ip[0] & 0xf0) == 0x60 && ip.length >= 40 && (ip[24] & 0xff) == 0xff)
                    continue; // IPv6 multicast
                  if ((ip[0] & 0xf0) == 0x40 && ip.length >= 20 && (ip[16] & 0xf0) == 0xe0)
                    continue; // IPv4 multicast
                  int chan = ((ip[0] & 0xf0) == 0x60) ? 0x0001 : 0x0003;
                  byte[] f = new byte[2 + ip.length];
                  f[0] = 0; // high byte of the 2-byte channel tag (chan is 0x0001 or 0x0003)
                  f[1] = (byte) chan;
                  System.arraycopy(ip, 0, f, 2, ip.length);
                  dtls.send(f, 0, f.length);
                }
              } catch (Throwable e) {
                if (running) System.out.println("tun->dtls ended: " + e);
              }
            },
            "tun->dtls");
    t.setDaemon(true);
    t.start();
  }

  /**
   * Keepalive pump (daemon): every ~2 s sends an unsolicited IPv6 Neighbour Advertisement for our
   * ULA (channel {@code 0x0001}) so the device keeps a neighbour entry for us and routes return
   * traffic back through the tunnel. See {@link #buildNeighbourAdvertisement}.
   */
  @SuppressWarnings("BusyWait") // intentional ~2 s keepalive cadence, not a busy-wait
  private void startNeighbourAdvertiser() {
    Thread t =
        new Thread(
            () -> {
              try {
                while (running) {
                  byte[] p = buildNeighbourAdvertisement();
                  byte[] f = new byte[2 + p.length];
                  f[1] = 0x01; // channel 0x0001 (IPv6)
                  System.arraycopy(p, 0, f, 2, p.length);
                  dtls.send(f, 0, f.length);
                  Thread.sleep(2000);
                }
              } catch (Throwable ignored) {
              }
            },
            "na");
    t.setDaemon(true);
    t.start();
  }

  /**
   * Builds an unsolicited ICMPv6 Neighbour Advertisement (type 136) announcing {@link #ourUla} to
   * the device, with a correct checksum over the IPv6 pseudo-header, so the device's neighbour
   * cache stays populated and it routes return traffic to us over the tunnel.
   *
   * @return the 64-byte IPv6 packet (40-byte header + 24-byte NA), ready to tag with channel 0x0001
   */
  private byte[] buildNeighbourAdvertisement() {
    byte[] our = ourUlaBytes;
    byte[] dev = deviceUlaBytes;
    byte[] icmp = new byte[24];
    icmp[0] = (byte) 136; // ICMPv6 Neighbour Advertisement
    icmp[4] = (byte) 0x20; // flags: Override
    System.arraycopy(our, 0, icmp, 8, 16); // target address = our ULA
    byte[] ip = new byte[64];
    ip[0] = 0x60; // IPv6, version 6
    ip[5] = 24; // payload length = 24 (the ICMPv6 message)
    ip[6] = 58; // next header = ICMPv6
    ip[7] = (byte) 255; // hop limit 255 (required for NDP)
    System.arraycopy(our, 0, ip, 8, 16); // src = our ULA
    System.arraycopy(dev, 0, ip, 24, 16); // dst = device ULA
    byte[] ph = new byte[64]; // pseudo-header for the ICMPv6 checksum
    System.arraycopy(our, 0, ph, 0, 16);
    System.arraycopy(dev, 0, ph, 16, 16);
    ph[35] = 24; // upper-layer length
    ph[39] = 58; // next header = ICMPv6
    System.arraycopy(icmp, 0, ph, 40, 24);
    int cs = Hex.checksum(ph, 0, ph.length);
    icmp[2] = (byte) (cs >> 8);
    icmp[3] = (byte) cs;
    System.arraycopy(icmp, 0, ip, 40, 24);
    return ip;
  }

  /**
   * The one-shot lifecycle transition out of a live/connecting state: records {@code target} and
   * {@code cause}, stops the data-plane threads, closes the transports, and (when {@code notify})
   * fires {@link #onClosed} once with {@code cause}. A second call is a no-op, so a user {@link
   * #close} racing a drop resolves to whichever happens first.
   */
  private void stop(State target, Throwable cause, boolean notify) {
    Consumer<Throwable> listener;
    synchronized (lifecycle) {
      if (state == State.CLOSED || state == State.FAILED) return; // already terminated — once only
      state = target;
      failure = cause;
      running = false;
      listener = onClosed;
    }
    teardown();
    if (notify && listener != null) {
      try {
        listener.accept(cause);
      } catch (Throwable ignore) {
      }
    }
  }

  /**
   * Closes the utun, DTLS, UDP, and TLS transports. Best-effort and idempotent (all null-guarded).
   */
  private void teardown() {
    if (tun != null) tun.close();
    try {
      if (dtls != null) dtls.close();
    } catch (Throwable ignore) {
    }
    if (udp != null) udp.close();
    try {
      if (controlProto != null) controlProto.close();
    } catch (Throwable ignore) {
    }
    try {
      if (controlSocket != null) controlSocket.close();
    } catch (Throwable ignore) {
    }
  }

  /** Where a handle is in its lifecycle. */
  public enum State {
    /** Bring-up in progress (or not yet started). */
    CONNECTING,
    /** The data plane is live; the device ULA ({@link #deviceUla()}) is reachable. */
    UP,
    /** Torn down by {@link #close} (or an interim {@link #restart} stop). */
    CLOSED,
    /** The link dropped on its own; see {@link #failure()}. */
    FAILED
  }
}
