package com.jbraddockm.tunnel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves a tunnel client identity ({@link Tunnels.Identity}) from the daemon's own configuration,
 * for when a caller does not supply the certificate itself. Used by the broker daemon's fallback and
 * by the {@link Cli} demo. Keeps the binary portable: nothing is embedded — the identity is always a
 * device's own cert read from disk.
 *
 * <p>Precedence, highest first:
 *
 * <ol>
 *   <li><b>Explicit paths</b> — {@code --cert}/{@code --key} CLI args.
 *   <li><b>Environment</b> — {@code BB_TUNNEL_CERT} + {@code BB_TUNNEL_KEY}.
 *   <li><b>Device folder</b> — a PIN (the request's, else {@code --device}/{@code BB_DEVICE}) under the
 *       devices directory ({@code --devices}/{@code BB_DEVICES_DIR}, else {@code devices/} beside the
 *       binary), i.e. {@code <devices>/<PIN>/}; or, with no PIN and exactly one device folder present,
 *       that one.
 * </ol>
 *
 * <p>Inside a {@code <PIN>/} folder the tunnel identity is {@code tunnel_cert.pem} + {@code
 * tunnel_key.pem}, falling back to the legacy {@code myleaf.pem} + {@code myleafkey.pem}.
 */
public final class DeviceIdentitySources {

  private static final List<String[]> CANDIDATES =
      List.of(
          new String[] {"tunnel_cert.pem", "tunnel_key.pem"},
          new String[] {"myleaf.pem", "myleafkey.pem"});

  private final Path explicitCert;
  private final Path explicitKey;
  private final String pin;
  private final Path devicesDir;

  private DeviceIdentitySources(Path explicitCert, Path explicitKey, String pin, Path devicesDir) {
    this.explicitCert = explicitCert;
    this.explicitKey = explicitKey;
    this.pin = pin;
    this.devicesDir = devicesDir;
  }

  /** Builds the resolver from the process environment and the default devices directory. */
  public static DeviceIdentitySources fromEnvironment() {
    return fromEnvironment(System.getenv(), null, null, null, null);
  }

  /**
   * Builds the resolver from {@code env} and optional CLI overrides. {@code devicesArg} / {@code
   * BB_DEVICES_DIR} set the devices directory; otherwise it is {@code devices/} beside the running
   * binary (falling back to the working directory).
   */
  public static DeviceIdentitySources fromEnvironment(
      Map<String, String> env, Path certArg, Path keyArg, String deviceArg, Path devicesArg) {
    var cert = certArg != null ? certArg : pathEnv(env, "BB_TUNNEL_CERT");
    var key = keyArg != null ? keyArg : pathEnv(env, "BB_TUNNEL_KEY");
    var devicePin = deviceArg != null ? deviceArg : env.get("BB_DEVICE");
    var dir = devicesArg != null ? devicesArg : pathEnv(env, "BB_DEVICES_DIR");
    return new DeviceIdentitySources(
        cert, key, blankToNull(devicePin), dir != null ? dir : defaultDevicesDir());
  }

  /**
   * The identity to present, given an optional per-request PIN (which wins over the configured one).
   * Empty when the daemon has no identity configured for that device.
   */
  public Optional<Tunnels.Identity> resolve(Optional<String> requestPin) {
    if (explicitCert != null && explicitKey != null) {
      return Optional.of(new Tunnels.Identity(explicitCert, explicitKey));
    }
    var chosenPin = requestPin.map(DeviceIdentitySources::blankToNull).orElse(pin);
    return deviceFolder(chosenPin).flatMap(DeviceIdentitySources::identityIn);
  }

  /** The {@code <PIN>/} folder to read from: the named PIN, or the sole device folder when unique. */
  private Optional<Path> deviceFolder(String chosenPin) {
    if (chosenPin != null) {
      return Optional.of(devicesDir.resolve(chosenPin));
    }
    if (!Files.isDirectory(devicesDir)) {
      return Optional.empty();
    }
    try (var entries = Files.list(devicesDir)) {
      var folders = entries.filter(Files::isDirectory).toList();
      return folders.size() == 1 ? Optional.of(folders.getFirst()) : Optional.empty();
    } catch (IOException unreadable) {
      return Optional.empty();
    }
  }

  /** The first candidate cert/key pair that both exist in {@code folder}. */
  private static Optional<Tunnels.Identity> identityIn(Path folder) {
    for (var names : CANDIDATES) {
      var cert = folder.resolve(names[0]);
      var key = folder.resolve(names[1]);
      if (Files.isReadable(cert) && Files.isReadable(key)) {
        return Optional.of(new Tunnels.Identity(cert, key));
      }
    }
    return Optional.empty();
  }

  /** {@code devices/} beside the running binary, or beside the working directory if that is unknown. */
  private static Path defaultDevicesDir() {
    return ProcessHandle.current()
        .info()
        .command()
        .map(Path::of)
        .map(Path::getParent)
        .filter(parent -> parent != null)
        .map(parent -> parent.resolve("devices"))
        .orElse(Path.of("devices"));
  }

  private static Path pathEnv(Map<String, String> env, String name) {
    var value = blankToNull(env.get(name));
    return value == null ? null : Path.of(value);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
