package com.jbraddockm.link.daemon;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.session.ConnectionPath;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Which device programs each network path can reach, and which program each method needs. The two
 * are kept apart on purpose:
 *
 * <ul>
 *   <li>{@link #REACHABLE_BY_PATH} is a property of the transport — it does not mention a method
 *       and is the same for any tool that talks to the device, so it travels with the connection
 *       layer.
 *   <li>{@link #ENDPOINT_BY_METHOD} is a property of bb-link's methods, so it travels with them.
 * </ul>
 *
 * <p>The guard joins the two: a method whose program a path cannot reach is refused before the
 * connection is attempted, with a message that names the method, the path and the paths that would
 * reach it. What a reachable program then answers is not decided here — only whether it can be
 * reached at all.
 */
final class EndpointReachability {

  /**
   * The programs each path reaches. USB and the tunnel reach the whole interface; a plain Wi-Fi LAN
   * address reaches only the login, and an address of an unknown shape is not refused by its shape
   * (it is left to the connection itself), so it is treated as reaching everything.
   */
  private static final Map<ConnectionPath, Set<Endpoint>> REACHABLE_BY_PATH =
      Map.of(
          ConnectionPath.USB, EnumSet.allOf(Endpoint.class),
          ConnectionPath.TUNNEL, EnumSet.allOf(Endpoint.class),
          ConnectionPath.LAN, EnumSet.of(Endpoint.LOGIN),
          ConnectionPath.OTHER, EnumSet.allOf(Endpoint.class));

  /**
   * The program each session method needs. A method that asks the device nothing beyond what the
   * login already did ({@code getSmbCredentials}, which hands back the credentials of the latest
   * login) maps to {@link Endpoint#LOGIN}, so it is reachable wherever a session can be set up.
   */
  private static final Map<LinkMethod, Endpoint> ENDPOINT_BY_METHOD =
      Map.ofEntries(
          Map.entry(LinkMethod.GET_DEVICE_PROPERTIES, Endpoint.DYNAMIC_PROPERTIES),
          // discovery.cgi is served over USB and the tunnel but not the plain Wi-Fi LAN, so the
          // same reachability guard as the other management programs applies.
          Map.entry(LinkMethod.GET_DEVICE_CHARACTERISTICS, Endpoint.DISCOVERY),
          // wipe.cgi accepts TCP but closes without responding over the tunnel;
          // connect skips readPin on TUNNEL by policy (see LinkService.establish).
          Map.entry(LinkMethod.GET_PIN, Endpoint.WIPE),
          Map.entry(LinkMethod.GET_SMB_CREDENTIALS, Endpoint.LOGIN),
          Map.entry(LinkMethod.LIST_PIM_ACCOUNTS, Endpoint.PIM_SYNC),
          Map.entry(LinkMethod.FIND_PIM_ACCOUNT, Endpoint.PIM_SYNC),
          Map.entry(LinkMethod.GET_PIM_ITEMS, Endpoint.PIM_SYNC),
          Map.entry(LinkMethod.PAIR_DEVICE, Endpoint.PAIRING),
          Map.entry(LinkMethod.GET_AUTHENTICATION_CERTIFICATE, Endpoint.LOGIN),
          Map.entry(LinkMethod.INSTALL_PACKAGES, Endpoint.UPDATE),
          Map.entry(LinkMethod.UNINSTALL_PACKAGES, Endpoint.UPDATE));

  private EndpointReachability() {}

  /**
   * Checks that a method can run over a path.
   *
   * @param method the method about to run
   * @param path the path of the session it would run on
   * @return the error to fail with when the path cannot reach the method's program, or empty when
   *     it can
   */
  static Optional<ServiceError> refusalFor(LinkMethod method, ConnectionPath path) {
    var endpoint = ENDPOINT_BY_METHOD.get(method);
    if (endpoint == null || reaches(path, endpoint)) {
      return Optional.empty();
    }
    return Optional.of(
        new ServiceError(
            CoreErrorCode.COMMAND_FAILED,
            method.wireName()
                + " is not reachable over "
                + describe(path)
                + "; it is served only over "
                + reachingPaths(endpoint)
                + "."));
  }

  private static boolean reaches(ConnectionPath path, Endpoint endpoint) {
    return REACHABLE_BY_PATH.getOrDefault(path, Set.of()).contains(endpoint);
  }

  /** Where this computer is connected, in words for a message. */
  private static String describe(ConnectionPath path) {
    return switch (path) {
      case USB -> "the USB connection";
      case TUNNEL -> "the Wi-Fi tunnel";
      case LAN -> "a Wi-Fi LAN address";
      case OTHER -> "this connection";
    };
  }

  /** The paths that reach an endpoint, in words, for the end of a refusal message. */
  private static String reachingPaths(Endpoint endpoint) {
    var over =
        REACHABLE_BY_PATH.entrySet().stream()
            .filter(entry -> entry.getValue().contains(endpoint))
            .map(Map.Entry::getKey)
            .filter(path -> path != ConnectionPath.OTHER)
            .map(EndpointReachability::describe)
            .sorted()
            .toList();
    return switch (over.size()) {
      case 0 -> "no connection";
      case 1 -> over.getFirst();
      default -> String.join(" or ", over);
    };
  }
}
