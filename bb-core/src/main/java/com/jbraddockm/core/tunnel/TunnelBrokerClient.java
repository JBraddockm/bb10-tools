package com.jbraddockm.core.tunnel;

import com.jbraddockm.core.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * A client of the tunnel-broker daemon over its Unix-domain socket (see {@code
 * analysis/TUNNEL-BROKER-DAEMON-PLAN.md}). The consumer — bb-link or Blend — uses it to {@link
 * #acquire} a device's shared tunnel, learn the coordinates to connect with, and {@link #release}
 * the hold; a background reader delivers the broker's unsolicited {@link BrokerEvent}s (notably
 * {@code dropped}, which a consumer surfaces as "tunnel down").
 *
 * <p>Requests block for the reply, which for {@code acquire} includes the (slow) tunnel bring-up,
 * so the default timeout is generous. One connection serves many interleaved requests, correlated
 * by id. Not reused after {@link #close}.
 */
public final class TunnelBrokerClient implements AutoCloseable {

  /** The broker's default Unix-domain socket path when none is configured. */
  public static final Path DEFAULT_SOCKET = Path.of("/tmp/bb-tunneld.sock");

  /** Generous because an {@code acquire} that opens a tunnel waits on the P-521 handshake. */
  private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

  private final SocketChannel channel;
  private final BufferedReader reader;
  private final PrintStream out;
  private final Duration timeout;
  private final AtomicLong ids = new AtomicLong();
  private final Map<Long, CompletableFuture<Map<String, Object>>> pending =
      new ConcurrentHashMap<>();
  private volatile Consumer<BrokerEvent> listener;

  private TunnelBrokerClient(SocketChannel channel, Duration timeout) {
    this.channel = channel;
    this.timeout = timeout;
    this.reader =
        new BufferedReader(
            new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8));
    this.out = new PrintStream(Channels.newOutputStream(channel), false, StandardCharsets.UTF_8);
  }

  /** Connects to the broker at {@code socket} with the default request timeout. */
  public static TunnelBrokerClient connect(Path socket) throws IOException {
    return connect(socket, DEFAULT_TIMEOUT);
  }

  /** Connects to the broker at {@code socket} with an explicit request timeout. */
  public static TunnelBrokerClient connect(Path socket, Duration timeout) throws IOException {
    var channel = SocketChannel.open(StandardProtocolFamily.UNIX);
    channel.connect(UnixDomainSocketAddress.of(socket));
    var client = new TunnelBrokerClient(channel, timeout);
    Thread.ofPlatform().daemon().name("broker-client").start(client::readLoop);
    return client;
  }

  private static String string(Object value) {
    return value instanceof String text ? text : null;
  }

  private static int integer(Object value) {
    return value instanceof Long number ? number.intValue() : 0;
  }

  private static List<String> strings(Object value) {
    var result = new ArrayList<String>();
    if (value instanceof List<?> items) {
      for (var item : items) {
        if (item instanceof String text) {
          result.add(text);
        }
      }
    }
    return List.copyOf(result);
  }

  /** Registers the listener for unsolicited events ({@code dropped}, {@code identity}, hello). */
  public void onEvent(Consumer<BrokerEvent> listener) {
    this.listener = listener;
  }

  /**
   * Opens or joins a device's tunnel by its transport {@code host} (USB host−1 or the Wi-Fi LAN
   * address). {@code stable} pins the constant {@code 169.254.1.1} SMB address.
   */
  public BrokerLease acquire(String host, boolean stable, String consumer) throws IOException {
    return lease(
        request("acquire", Json.object("host", host, "stable", stable, "consumer", consumer)));
  }

  /** Rejoins a device whose GUID the broker already recorded (via an earlier {@link #identify}). */
  public BrokerLease acquireByGuid(String guid, String consumer) throws IOException {
    return lease(request("acquire", Json.object("guid", guid, "consumer", consumer)));
  }

  /** Releases a hold by its token. Idempotent on the broker. */
  public void release(String token) throws IOException {
    request("release", Json.object("token", token));
  }

  // --- internals -------------------------------------------------------------------------------

  /**
   * Reports a device's GUID/PIN (learned over {@code discovery.cgi}) so the broker can record it.
   */
  public void identify(String host, String guid, String pin) throws IOException {
    request("identify", Json.object("host", host, "guid", guid, "pin", pin));
  }

  /** The tunnels the broker currently holds. */
  public List<TunnelInfo> status() throws IOException {
    var reply = request("status", Json.object());
    var tunnels = new ArrayList<TunnelInfo>();
    if (reply.get("tunnels") instanceof List<?> rows) {
      for (var row : rows) {
        if (row instanceof Map<?, ?> t) {
          tunnels.add(
              new TunnelInfo(
                  string(t.get("host")),
                  string(t.get("state")),
                  string(t.get("ula")),
                  string(t.get("innerIpv4")),
                  string(t.get("guid")),
                  string(t.get("pin")),
                  integer(t.get("refs")),
                  strings(t.get("consumers"))));
        }
      }
    }
    return List.copyOf(tunnels);
  }

  @Override
  public void close() {
    try {
      channel.close();
    } catch (IOException ignore) {
      // the reader loop also ends when the channel closes
    }
  }

  private BrokerLease lease(Map<String, Object> reply) {
    return new BrokerLease(
        string(reply.get("token")),
        string(reply.get("ula")),
        string(reply.get("innerIpv4")),
        string(reply.get("guid")),
        string(reply.get("pin")),
        integer(reply.get("refs")));
  }

  private Map<String, Object> request(String op, Map<String, Object> params) throws IOException {
    var id = ids.incrementAndGet();
    var future = new CompletableFuture<Map<String, Object>>();
    pending.put(id, future);
    try {
      writeLine(Json.object("id", id, "op", op, "params", params));
      var reply = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (Boolean.TRUE.equals(reply.get("ok"))) {
        return reply;
      }
      var error = reply.get("error") instanceof Map<?, ?> e ? e : Map.of();
      throw new BrokerClientException(string(error.get("code")), string(error.get("message")));
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
      throw new IOException("interrupted waiting for the broker reply", interrupted);
    } catch (ExecutionException failed) {
      throw new IOException("the broker connection failed", failed.getCause());
    } catch (TimeoutException slow) {
      throw new IOException("the broker did not reply within " + timeout, slow);
    } finally {
      pending.remove(id);
    }
  }

  private void writeLine(Map<String, Object> message) {
    var bytes = (Json.write(message) + "\n").getBytes(StandardCharsets.UTF_8);
    synchronized (out) {
      out.write(bytes, 0, bytes.length);
      out.flush();
    }
  }

  @SuppressWarnings("unchecked")
  private void readLoop() {
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        if (line.isBlank() || !(Json.parse(line) instanceof Map<?, ?> message)) {
          continue;
        }
        var map = (Map<String, Object>) message;
        if (map.get("event") instanceof String type) {
          deliver(type, map);
        } else if (map.get("id") instanceof Long id) {
          var future = pending.get(id);
          if (future != null) {
            future.complete(map);
          }
        }
      }
    } catch (IOException closed) {
      // the connection ended; pending requests are failed below
    } finally {
      var closed = new IOException("the broker connection closed");
      pending.values().forEach(future -> future.completeExceptionally(closed));
    }
  }

  private void deliver(String type, Map<String, Object> event) {
    var callback = listener;
    if (callback != null) {
      callback.accept(
          new BrokerEvent(
              type,
              string(event.get("host")),
              string(event.get("guid")),
              string(event.get("pin")),
              string(event.get("cause"))));
    }
  }
}
