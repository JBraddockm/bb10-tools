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
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.actions.DevicePin;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.discovery.DeviceScanner;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.session.DeviceSession;
import com.jbraddockm.link.session.DeviceTarget;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.LoginOnly;
import com.jbraddockm.link.session.SmbCredentials;
import com.jbraddockm.link.session.TransportScheme;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** The bb-link service: what the shared daemon server runs. */
public final class LinkService implements Service<DeviceSession> {

  /** The version reported in {@code hello}. */
  public static final String VERSION = "0.1.0";

  private final TransportScheme scheme;
  private final DeviceScanner scanner;
  private final ExchangeObserver observer;

  /** The sessions that were opened to log in and do nothing else, until they are established. */
  private final Set<DeviceSession> loginOnly = ConcurrentHashMap.newKeySet();

  /**
   * Creates the production service: HTTPS, and the original tool's detector.
   *
   * @param observer told about every exchange with a device, for example to record a trace
   */
  public LinkService(ExchangeObserver observer) {
    this(TransportScheme.HTTPS, DeviceScanner.library(), observer);
  }

  /** Creates a service with its collaborators replaced, which tests use. */
  LinkService(TransportScheme scheme, DeviceScanner scanner, ExchangeObserver observer) {
    this.scheme = scheme;
    this.scanner = scanner;
    this.observer = observer;
  }

  @Override
  public ServiceInfo info() {
    return new ServiceInfo("bb-link", VERSION);
  }

  @Override
  public List<CommandSpec<DeviceSession>> commands() {
    return Arrays.stream(LinkMethod.values()).map(method -> method.spec(scanner)).toList();
  }

  @Override
  public DeviceSession open(RequestParams params) {
    var target =
        new DeviceTarget(
            params.requireString("host"),
            params.integer("port", DeviceTarget.DEFAULT_PORT, 1, 65_535),
            scheme,
            AuthenticationReader.read(params));
    var describeDevice = params.flag("deviceInfo", true);
    var session = DeviceSession.forTarget(target, observer);
    if (!describeDevice) {
      loginOnly.add(session);
    }
    return session;
  }

  /**
   * Logs in and reads the device, then reads its PIN. Failing to read the PIN does not prevent the
   * session: the summary then simply has no {@code pin}. A session that was opened with {@code
   * deviceInfo} set to {@code false} only logs in, and the result has no device.
   */
  @Override
  public Establishment establish(DeviceSession session, Reply reply) {
    if (loginOnly.remove(session)) {
      return switch (session.execute(new LoginOnly(), reply)) {
        case CommandOutcome.Succeeded<NoData> _ ->
            new Establishment.Established(described(session, Optional.empty()));
        case CommandOutcome.Failed<NoData>(var error, _, _) -> new Establishment.Rejected(error);
      };
    }
    return switch (session.execute(new GetDeviceProperties(), reply)) {
      case CommandOutcome.Succeeded<DeviceProperties>(var properties, _) -> {
        var summary = DeviceSummary.of(properties, readPin(session, reply));
        yield new Establishment.Established(described(session, Optional.of(summary)));
      }
      case CommandOutcome.Failed<DeviceProperties>(var error, _, _) ->
          new Establishment.Rejected(error);
    };
  }

  /** The result of a connection; the device is {@code null} when nothing of it was read. */
  private static Map<String, Object> described(
      DeviceSession session, Optional<DeviceSummary> summary) {
    return Json.object(
        "host", session.target().host(),
        "port", session.target().port(),
        "authentication", session.target().authentication().type().wireName(),
        "device", summary.map(DeviceSummary::toJson).orElse(null),
        "smb", session.smbCredentials().map(SmbCredentials::toJson).orElse(null));
  }

  private static Optional<DevicePin> readPin(DeviceSession session, Reply reply) {
    return switch (session.execute(new GetPin(), reply)) {
      case CommandOutcome.Succeeded<DevicePin>(var pin, _) -> Optional.of(pin);
      case CommandOutcome.Failed<DevicePin>(var error, _, _) -> {
        reply.onLog(LogLevel.WARN, "Could not read the device PIN: " + error.message());
        yield Optional.empty();
      }
    };
  }
}
