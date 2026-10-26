package com.jbraddockm.tunnel.broker;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Serves {@link TunnelBroker} to many clients over a Unix-domain socket, one connection per client.
 * Each connection is read on its own thread; a request is one JSON object per line and is answered
 * with one reply carrying the same {@code id}. The broker also pushes unsolicited events ({@code
 * dropped}, {@code identity}) down whichever connections hold the device, so a connection's output
 * is written under a per-connection lock.
 *
 * <p>A connection's close — clean or a crash — releases every lease it still held (via {@link
 * TunnelBroker#disconnected}), so no client can pin a tunnel up by dying.
 */
public final class BrokerServer implements AutoCloseable {

  /** The line protocol version announced in {@code hello}. */
  public static final int PROTOCOL_VERSION = 1;

  private static final List<String> OPS = List.of("acquire", "status", "release", "identify");

  private final TunnelBroker broker;
  private final Path socketPath;
  private final String version;
  private final AtomicLong connections = new AtomicLong();

  private volatile ServerSocketChannel server;
  private volatile boolean running;

  /**
   * @param broker the shared broker core
   * @param socketPath the Unix-domain socket to bind (a stale file at the path is removed first)
   * @param version the daemon version reported in {@code hello}
   */
  public BrokerServer(TunnelBroker broker, Path socketPath, String version) {
    this.broker = broker;
    this.socketPath = socketPath;
    this.version = version;
  }

  /** Binds the socket and accepts connections until {@link #close}. Blocks the calling thread. */
  public void serve() throws IOException {
    Files.deleteIfExists(socketPath);
    server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
    server.bind(UnixDomainSocketAddress.of(socketPath));
    grantSocketAccess(socketPath);
    running = true;
    try {
      while (running) {
        SocketChannel channel;
        try {
          channel = server.accept();
        } catch (ClosedChannelException stopping) {
          break; // close() was called (AsynchronousCloseException is a subtype)
        }
        var id = "c" + connections.incrementAndGet();
        Thread.ofPlatform().daemon().name("broker-" + id).start(() -> handle(id, channel));
      }
    } finally {
      running = false;
      Files.deleteIfExists(socketPath);
    }
  }

  /**
   * Makes the freshly-bound socket reachable by its client. Launched with {@code sudo}, the daemon
   * runs as root, so the default root-owned {@code 0755} socket is not connectable by an unprivileged
   * consumer (connecting to a Unix socket needs write permission). Hand it to the real invoking user
   * ({@code SUDO_USER}) with owner-only {@code rw}; if that cannot be done, fall back to opening it to
   * all local users so a dev run still works. Not under sudo, the daemon already runs as the
   * connecting user, so the default owner access is left as it is.
   */
  private static void grantSocketAccess(Path socket) {
    var sudoUser = System.getenv("SUDO_USER");
    if (sudoUser == null || sudoUser.isBlank()) {
      return;
    }
    try {
      var owner =
          FileSystems.getDefault().getUserPrincipalLookupService().lookupPrincipalByName(sudoUser);
      Files.setOwner(socket, owner);
      Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-------"));
    } catch (IOException | UnsupportedOperationException restrict) {
      System.err.println(
          "bb-tunneld: could not restrict the socket to "
              + sudoUser
              + " ("
              + restrict
              + "); opening it to local users instead");
      try {
        Files.setPosixFilePermissions(socket, PosixFilePermissions.fromString("rw-rw-rw-"));
      } catch (IOException open) {
        System.err.println("bb-tunneld: could not set socket permissions: " + open);
      }
    }
  }

  @Override
  public void close() {
    running = false;
    var listener = server;
    if (listener != null) {
      try {
        listener.close();
      } catch (IOException ignore) {
        // best effort: the accept loop also observes running=false
      }
    }
  }

  private void handle(String id, SocketChannel channel) {
    var out = new PrintStream(Channels.newOutputStream(channel), false, StandardCharsets.UTF_8);
    broker.connected(id, event -> writeLine(out, event));
    writeLine(
        out,
        Json.object(
            "event", "hello",
            "protocol", PROTOCOL_VERSION,
            "tool", "bb-tunneld",
            "version", version,
            "ops", OPS));
    try (var reader =
        new BufferedReader(
            new InputStreamReader(Channels.newInputStream(channel), StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        if (!line.isBlank()) {
          dispatch(id, line, out);
        }
      }
    } catch (IOException dropped) {
      // the client went away; the finally block reclaims its leases
    } finally {
      broker.disconnected(id);
      try {
        channel.close();
      } catch (IOException ignore) {
        // nothing more to do
      }
    }
  }

  private void dispatch(String id, String line, PrintStream out) {
    Object parsed;
    try {
      parsed = Json.parse(line);
    } catch (Json.JsonException malformed) {
      writeLine(out, error(null, "bad_json", malformed.getMessage()));
      return;
    }
    if (!(parsed instanceof Map<?, ?> request)) {
      writeLine(out, error(null, "bad_request", "a request must be a JSON object"));
      return;
    }
    var requestId = request.get("id");
    var op = request.get("op") instanceof String name ? name : null;
    var params =
        request.get("params") instanceof Map<?, ?> values
            ? new RequestParams(values)
            : new RequestParams(Map.of());
    try {
      var result =
          switch (op) {
            case "acquire" -> broker.acquire(id, params);
            case "status" -> broker.status(params);
            case "release" -> broker.release(params);
            case "identify" -> broker.identify(params);
            case null -> throw new BrokerException("bad_request", "a request must carry an 'op'");
            default -> throw new BrokerException("unknown_op", "unknown op '" + op + "'");
          };
      var reply = Json.object("id", requestId, "ok", true);
      reply.putAll(result);
      writeLine(out, reply);
    } catch (BrokerException refused) {
      writeLine(out, error(requestId, refused.code(), refused.getMessage()));
    } catch (InvalidArgumentException invalid) {
      writeLine(out, error(requestId, "invalid_argument", invalid.getMessage()));
    } catch (Exception failure) {
      writeLine(out, error(requestId, "internal", String.valueOf(failure)));
    }
  }

  private static Map<String, Object> error(Object requestId, String code, String message) {
    return Json.object(
        "id", requestId, "ok", false, "error", Json.object("code", code, "message", message));
  }

  /**
   * One JSON object per line. Synchronised on the stream so replies and events never interleave.
   */
  private static void writeLine(PrintStream out, Map<String, Object> message) {
    var bytes = (Json.write(message) + "\n").getBytes(StandardCharsets.UTF_8);
    synchronized (out) {
      out.write(bytes, 0, bytes.length);
      out.flush();
    }
  }
}
