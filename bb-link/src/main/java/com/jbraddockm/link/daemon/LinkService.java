package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.Establishment;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.daemon.Service;
import com.jbraddockm.core.daemon.ServiceInfo;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.tunnel.TunnelBrokerClient;
import com.jbraddockm.link.endpoint.characteristics.DeviceCharacteristics;
import com.jbraddockm.link.endpoint.characteristics.GetDeviceCharacteristics;
import com.jbraddockm.link.endpoint.discovery.DeviceScanner;
import com.jbraddockm.link.endpoint.discovery.FriendlyNameAdvertiser;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.ClientNames;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredPairing;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.transport.tunnel.BrokerTunnelConnector;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.session.DeviceTarget;
import com.jbraddockm.link.transport.session.ExchangeObserver;
import com.jbraddockm.link.transport.session.LoginOnly;
import com.jbraddockm.link.transport.session.SmbCredentials;
import com.jbraddockm.link.transport.session.TransportScheme;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** The bb-link service: what the shared daemon server runs. */
public final class LinkService implements Service<DeviceSession> {

  /** The version reported in {@code hello}. */
  public static final String VERSION = "0.1.0";

  private final TransportScheme scheme;
  private final DeviceScanner scanner;
  private final ExchangeObserver observer;
  private final Pairing pairing;
  private final CertificateRetrieval retrieval;

  /**
   * Creates the production service: HTTPS, and the original tool's detector.
   *
   * @param observer told about every exchange with a device, for example to record a trace
   */
  public LinkService(ExchangeObserver observer) {
    this(
        TransportScheme.HTTPS,
        DeviceScanner.library(),
        observer,
        new Pairing(),
        new CertificateRetrieval());
  }

  /** Creates a service with its collaborators replaced, which tests use. */
  LinkService(
      TransportScheme scheme,
      DeviceScanner scanner,
      ExchangeObserver observer,
      Pairing pairing,
      CertificateRetrieval retrieval) {
    this.scheme = scheme;
    this.scanner = scanner;
    this.observer = observer;
    this.pairing = pairing;
    this.retrieval = retrieval;
  }

  /** Whether a connect asks for the device to be read; it does unless it says {@code false}. */
  private static boolean describesDevice(RequestParams params) {
    return params.flag("deviceInfo", true);
  }

  /** The result of a connection; the device is {@code null} when nothing of it was read. */
  private static Map<String, Object> described(DeviceSession session, DeviceSummary summary) {
    return Json.object(
        "host", session.target().host(),
        "port", session.target().port(),
        "authentication", session.target().authentication().type().wireName(),
        "device", summary != null ? summary.toJson() : null,
        "smb", session.smbCredentials().map(SmbCredentials::toJson).orElse(null));
  }

  private static DeviceCharacteristics readCharacteristics(DeviceSession session, Reply reply) {
    return switch (session.execute(new GetDeviceCharacteristics(), reply)) {
      case CommandOutcome.Succeeded<DeviceCharacteristics>(var characteristics, _) ->
          characteristics;
      case CommandOutcome.Failed<DeviceCharacteristics>(var error, _, _) -> {
        reply.onLog(LogLevel.WARN, "Could not read the device characteristics: " + error.message());
        yield null;
      }
    };
  }

  /**
   * A Wi-Fi LAN (or otherwise routable) address is reached through a tunnel; USB and a ULA are not.
   */
  private static boolean needsTunnel(ConnectionPath path) {
    return path == ConnectionPath.LAN || path == ConnectionPath.OTHER;
  }

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-link", VERSION);
  }

  @Override
  public List<CommandSpec<DeviceSession>> commands() {
    return Arrays.stream(LinkMethod.values())
        .map(method -> method.spec(scanner, pairing, retrieval))
        .toList();
  }

  @Override
  public DeviceSession open(RequestParams params) {
    var target =
        new DeviceTarget(
            params.requireString("host"),
            params.integer("port", DeviceTarget.DEFAULT_PORT, 1, 65_535),
            scheme,
            AuthenticationReader.read(params));
    describesDevice(params); // refuses a malformed option while no session exists yet
    var session =
        DeviceSession.forTarget(target, observer, params.optionalString("smbHost").orElse(null));
    // `tunnel` opts a Wi-Fi LAN connect into a broker-managed tunnel: bb-link brings the tunnel up
    // (through the daemon) and reaches the device over its ULA, so the user connects with the LAN
    // address and never handles the ULA. USB and a direct ULA connect need no tunnel; login/pairing
    // over a plain LAN address stays tunnel-free, since bringing a tunnel up needs an enrolled
    // cert.
    if (params.flag("tunnel") && needsTunnel(target.path())) {
      var socket =
          params
              .optionalString("brokerSocket")
              .map(Path::of)
              .orElse(TunnelBrokerClient.DEFAULT_SOCKET);
      // The tunnel's client identity (the device's enrolled cert) is passed by path to the daemon, so
      // its binary embeds no device cert. Absent, the daemon falls back to its own resolution.
      var tunnelCert = params.optionalString("tunnelCertificate").map(Path::of).orElse(null);
      var tunnelKey = params.optionalString("tunnelKey").map(Path::of).orElse(null);
      session.useTunnel(
          new BrokerTunnelConnector(
              socket,
              target.host(),
              params.flag("stableSmb"),
              "bb-link",
              tunnelCert,
              tunnelKey));
      advertiseFriendlyName(session, tunnelCert);
    }
    return session;
  }

  /**
   * Advertises this workstation's friendly name over mDNS for the device this tunnel connect pairs
   * with, so the device shows it in Device Connections instead of the {@code Desktop-XXXX} fallback.
   * Best-effort, and attached to the session so the advert is withdrawn when the connection ends. A
   * connect without a pairing beside the tunnel certificate advertises nothing.
   */
  private static void advertiseFriendlyName(DeviceSession session, Path tunnelCertificate) {
    pairedDeviceId(tunnelCertificate).ifPresent(id -> advertiseFriendlyName(session, id));
  }

  /**
   * Starts the friendly-name advert for an explicit paired {@code deviceId} and attaches it to the
   * session. Used right after a pairing, when the device is adding this computer and so is most
   * likely to be browsing for our Friendly service. Best-effort — it never throws.
   *
   * @param session the session the advert lives with; it is withdrawn when the session closes
   * @param deviceId the identifier the device stored for this computer (which it folds to match us)
   */
  static void advertiseFriendlyName(DeviceSession session, String deviceId) {
    if (deviceId == null || deviceId.isBlank()) {
      return;
    }
    FriendlyNameAdvertiser.tryStart(ClientNames.localWorkstation(), deviceId)
        .ifPresent(session::attach);
  }

  /**
   * The identifier the device stored for this computer, read from the {@code pairing.json} beside
   * the tunnel certificate: the {@code deviceId} the pairing gave itself, which the device folds to
   * match our Friendly advert. Empty when there is no certificate or no readable pairing beside it.
   *
   * @param tunnelCertificate the path the connect passed, or {@code null}
   * @return the identifier, or empty
   */
  static Optional<String> pairedDeviceId(Path tunnelCertificate) {
    if (tunnelCertificate == null) {
      return Optional.empty();
    }
    var pairingFile = tunnelCertificate.resolveSibling(PairingDirectory.METADATA_FILE);
    try {
      return Optional.of(StoredPairing.fromJsonText(Files.readString(pairingFile)).deviceId());
    } catch (IOException | RuntimeException noPairing) {
      return Optional.empty();
    }
  }

  /**
   * Logs in and reads the device, then reads its PIN. Failing to read the PIN does not prevent the
   * session: the summary then simply has no {@code pin}. A connect with {@code deviceInfo} set to
   * {@code false} only logs in, and the result has no device.
   */
  @Override
  public Establishment establish(DeviceSession session, RequestParams params, Reply reply) {
    try {
      session.establishTunnel(reply); // no-op unless this connect asked for a broker tunnel
    } catch (IOException tunnelFailure) {
      return new Establishment.Rejected(
          new ServiceError(
              CoreErrorCode.COMMAND_FAILED,
              "The Wi-Fi tunnel to the device could not be brought up: "
                  + tunnelFailure.getMessage()));
    }
    if (!describesDevice(params)) {
      return switch (session.execute(new LoginOnly(), reply)) {
        case CommandOutcome.Succeeded<NoData> _ ->
            new Establishment.Established(described(session, null));
        case CommandOutcome.Failed<NoData>(var error, _, _) -> new Establishment.Rejected(error);
      };
    }
    var refusal =
        EndpointReachability.refusalFor(LinkMethod.GET_DEVICE_PROPERTIES, session.connectionPath());
    if (refusal.isPresent()) {
      return new Establishment.Rejected(refusal.orElseThrow());
    }
    return switch (session.execute(new GetDeviceProperties(), reply)) {
      case CommandOutcome.Succeeded<DeviceProperties>(var properties, _) -> {
        var characteristics = readCharacteristics(session, reply);
        var summary = DeviceSummary.of(properties, characteristics);
        yield new Establishment.Established(described(session, summary));
      }
      case CommandOutcome.Failed<DeviceProperties>(var error, _, _) ->
          new Establishment.Rejected(error);
    };
  }
}
