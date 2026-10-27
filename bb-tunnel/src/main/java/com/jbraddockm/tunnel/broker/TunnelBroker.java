package com.jbraddockm.tunnel.broker;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.tunnel.Tunnels;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The shared, thread-safe core of the tunnel broker daemon: it layers consumer-facing leases,
 * identity and events on top of {@link Tunnels}, the one-shared-tunnel-per-device transport.
 * bb-link and Blend are peer consumers; neither owns a tunnel's lifecycle (see {@code
 * analysis/TUNNEL-BROKER-DAEMON-PLAN.md}).
 *
 * <p>This class is transport-agnostic — it speaks {@link RequestParams}/JSON maps and knows nothing
 * of sockets — so it is unit-tested directly. {@link BrokerServer} wraps it in a Unix-domain-socket
 * server, one connection per client.
 *
 * <h2>What it adds over {@link Tunnels}</h2>
 *
 * <ul>
 *   <li><b>Tokens &amp; connection-scoped leases.</b> Every {@code acquire} hands back an opaque
 *       token and is remembered against the connection that made it; when that connection goes away
 *       ({@link #disconnected}) its leases are released, so a crashed client cannot pin a tunnel up
 *       forever.
 *   <li><b>Identity.</b> A consumer that has learned a device's GUID/PIN (over {@code
 *       discovery.cgi}, which only it can reach) reports them with {@code identify}; the broker
 *       records {@code GUID → host} so the {@code acquire {guid}} rejoin form works, and tells the
 *       device's other consumers with an {@code identity} event.
 *   <li><b>Drop events.</b> When a tunnel drops on its own, {@link Tunnels#onDrop} fires; the
 *       broker forgets the device and sends a {@code dropped} event to the consumers that held it.
 * </ul>
 */
final class TunnelBroker {

  private final Tunnels tunnels;
  private final Object lock = new Object();
  private final AtomicLong tokens = new AtomicLong();
  private final Map<String, Hold> byToken = new HashMap<>();
  private final Map<String, DeviceInfo> byHost = new HashMap<>();
  private final Map<String, String> hostByGuid = new HashMap<>();
  private final Map<String, EventSink> connections = new HashMap<>();

  /** Wraps the tunnel service and subscribes to its drop notifications. */
  TunnelBroker(Tunnels tunnels) {
    this.tunnels = Objects.requireNonNull(tunnels, "tunnels");
    tunnels.onDrop(this::dropped);
  }

  /** Delivers an event to each sink outside the lock; a failed write never breaks the others. */
  private static void fan(List<EventSink> sinks, Map<String, Object> event) {
    for (var sink : sinks) {
      try {
        sink.event(event);
      } catch (RuntimeException ignore) {
        // a wedged or closed connection must not stop the rest being told
      }
    }
  }

  /** Registers a connection and where to write its events. Call before handling its requests. */
  void connected(String connection, EventSink events) {
    synchronized (lock) {
      connections.put(connection, events);
    }
  }

  /** Forgets a connection and releases every lease it still held (its socket closed). */
  void disconnected(String connection) {
    List<String> toRelease;
    synchronized (lock) {
      connections.remove(connection);
      toRelease =
          byToken.values().stream()
              .filter(hold -> hold.connection.equals(connection))
              .map(Hold::token)
              .toList();
      toRelease.forEach(this::releaseLocked);
    }
  }

  /**
   * Opens or joins a device's tunnel. Keyed by {@code host} (the transport address) or, in the
   * rejoin form, by a {@code guid} the broker already recorded. Returns the coordinates to connect
   * with plus the lease {@code token}. The slow bring-up runs without the broker lock, so other
   * connections are not blocked by it.
   */
  Map<String, Object> acquire(String connection, RequestParams params) throws Exception {
    var consumer = params.optionalString("consumer").orElse("unknown");
    var stable = params.flag("stable");
    var guid = params.optionalString("guid");
    String host;
    if (guid.isPresent()) {
      synchronized (lock) {
        host = hostByGuid.get(guid.get());
      }
      if (host == null) {
        throw new BrokerException("unknown_guid", "no tunnel is recorded for guid " + guid.get());
      }
    } else {
      host = params.requireString("host");
    }

    var lease = tunnels.acquire(host, stable); // may block on bring-up; no broker lock held

    synchronized (lock) {
      var info = byHost.computeIfAbsent(host, _ -> new DeviceInfo());
      info.ula = lease.deviceUla();
      info.innerIpv4 = lease.deviceIpv4();
      var token = "t" + tokens.incrementAndGet();
      byToken.put(token, new Hold(token, host, connection, consumer, lease));
      return describe(token, host, info);
    }
  }

  /** Releases one lease by token. Idempotent; reports the references remaining for the device. */
  Map<String, Object> release(RequestParams params) {
    var token = params.requireString("token");
    synchronized (lock) {
      return Json.object("refs", (long) releaseLocked(token));
    }
  }

  /** Lists the tunnels the broker holds (optionally one {@code host}). */
  Map<String, Object> status(RequestParams params) {
    var only = params.optionalString("host");
    synchronized (lock) {
      var tunnels = new ArrayList<Object>();
      byHost.forEach(
          (host, info) -> {
            if (only.isPresent() && !only.get().equals(host)) {
              return;
            }
            var holds = holdsFor(host);
            var consumers = new LinkedHashSet<String>();
            holds.forEach(hold -> consumers.add(hold.consumer));
            tunnels.add(
                Json.object(
                    "host",
                    host,
                    "state",
                    "UP",
                    "ula",
                    info.ula,
                    "innerIpv4",
                    info.innerIpv4,
                    "guid",
                    info.guid,
                    "pin",
                    info.pin,
                    "refs",
                    (long) holds.size(),
                    "consumers",
                    List.copyOf(consumers)));
          });
      return Json.object("tunnels", tunnels);
    }
  }

  /**
   * Records the GUID/PIN a consumer learned for a device it holds, so the {@code acquire {guid}}
   * rejoin form resolves and the device's other consumers get an {@code identity} event. The broker
   * never reads {@code discovery.cgi} itself — only a consumer can, over the tunnel — so identity
   * arrives this way.
   */
  Map<String, Object> identify(RequestParams params) {
    var host = params.requireString("host");
    var guid = params.requireString("guid");
    var pin = params.optionalString("pin").orElse(null);
    List<EventSink> sinks;
    Map<String, Object> event;
    synchronized (lock) {
      var info = byHost.get(host);
      if (info == null) {
        throw new BrokerException("unknown_host", "no tunnel is held for host " + host);
      }
      info.guid = guid;
      info.pin = pin;
      hostByGuid.put(guid, host);
      event = Json.object("event", "identity", "host", host, "guid", guid, "pin", pin);
      sinks = subscribers(host);
    }
    fan(sinks, event);
    return Json.object("ok", true);
  }

  /** Called by {@link Tunnels} when a tunnel drops on its own. */
  private void dropped(String host, Throwable cause) {
    List<EventSink> sinks;
    Map<String, Object> event;
    synchronized (lock) {
      // The tunnel is already gone and the manager has evicted it; just forget our holds for it
      // (closing their dead leases is pointless) and notify whoever held it.
      sinks = subscribers(host);
      holdsFor(host).forEach(hold -> byToken.remove(hold.token));
      byHost.remove(host);
      hostByGuid.values().removeIf(host::equals);
      event =
          Json.object(
              "event",
              "dropped",
              "host",
              host,
              "cause",
              cause == null ? null : String.valueOf(cause));
    }
    fan(sinks, event);
  }

  /** Caller holds {@link #lock}. Returns the references remaining for the released device. */
  private int releaseLocked(String token) {
    var hold = byToken.remove(token);
    if (hold == null) {
      return 0; // already released, or never existed — idempotent
    }
    hold.lease.close(); // tunnel ref--; brings the tunnel down on the last lease
    var remaining = holdsFor(hold.host).size();
    if (remaining == 0) {
      byHost.remove(hold.host);
      hostByGuid.values().removeIf(hold.host::equals);
    }
    return remaining;
  }

  // --- internals -------------------------------------------------------------------------------

  /** Caller holds {@link #lock}. */
  private List<Hold> holdsFor(String host) {
    return byToken.values().stream().filter(hold -> hold.host.equals(host)).toList();
  }

  /** Caller holds {@link #lock}. The event sinks of the connections holding {@code host}. */
  private List<EventSink> subscribers(String host) {
    var sinks = new ArrayList<EventSink>();
    var seen = new LinkedHashSet<String>();
    for (var hold : holdsFor(host)) {
      if (seen.add(hold.connection)) {
        var sink = connections.get(hold.connection);
        if (sink != null) {
          sinks.add(sink);
        }
      }
    }
    return sinks;
  }

  /** Caller holds {@link #lock}. */
  private Map<String, Object> describe(String token, String host, DeviceInfo info) {
    return Json.object(
        "token", token,
        "host", host,
        "ula", info.ula,
        "innerIpv4", info.innerIpv4,
        "guid", info.guid,
        "pin", info.pin,
        "refs", (long) holdsFor(host).size());
  }

  /**
   * Where a connection's events (server-initiated {@code dropped}/{@code identity}) are written.
   */
  @FunctionalInterface
  interface EventSink {
    void event(Map<String, Object> event);
  }

  /** One consumer hold on a device's tunnel. */
  private record Hold(
      String token, String host, String connection, String consumer, Tunnels.Lease lease) {}

  /**
   * What the broker knows about a device whose tunnel is up: its coordinates and (later) identity.
   */
  private static final class DeviceInfo {
    String ula;
    String innerIpv4;
    String guid; // null until an identify
    String pin; // null until an identify
  }
}
