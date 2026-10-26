package com.jbraddockm.link.daemon;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarKind;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.daemon.SessionCommand;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.discovery.DeviceDiscovery;
import com.jbraddockm.link.discovery.DeviceScanner;
import com.jbraddockm.link.pairing.CertificateRetrieval;
import com.jbraddockm.link.pairing.CertificateRetrievalRequest;
import com.jbraddockm.link.pairing.GetAuthenticationCertificate;
import com.jbraddockm.link.pairing.InspectCertificate;
import com.jbraddockm.link.pairing.PairWithDevice;
import com.jbraddockm.link.pairing.Pairing;
import com.jbraddockm.link.pairing.PairingRequest;
import com.jbraddockm.link.pimsync.FindPimAccount;
import com.jbraddockm.link.pimsync.GetPimItems;
import com.jbraddockm.link.pimsync.ListPimAccounts;
import com.jbraddockm.link.pimsync.PimFilters;
import com.jbraddockm.link.pimsync.PimService;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.session.DeviceCommand;
import com.jbraddockm.link.session.DeviceSession;
import com.jbraddockm.link.update.BarSelection;
import com.jbraddockm.link.update.InspectPackage;
import com.jbraddockm.link.update.InstallPackages;
import com.jbraddockm.link.update.InstallResult;
import com.jbraddockm.link.update.UninstallPackages;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * The methods bb-link offers beyond {@code connect}, {@code disconnect} and {@code shutdown}. Each
 * constant knows its wire name and how to prepare its command.
 */
enum LinkMethod {

  /** Reads the device's identity and installed packages. */
  GET_DEVICE_PROPERTIES("getDeviceProperties"),

  /** Reads the device PIN. */
  GET_PIN("getPin"),

  /**
   * Returns the file share credentials of the session's latest login. Asks the device nothing: the
   * credentials come from the login.
   */
  GET_SMB_CREDENTIALS("getSmbCredentials"),

  /** Lists the accounts of the device's personal information sync. */
  LIST_PIM_ACCOUNTS("listPimAccounts"),

  /** Looks up the account of a workstation in the personal information sync. */
  FIND_PIM_ACCOUNT("findPimAccount"),

  /** Reads contacts, events or calendars. */
  GET_PIM_ITEMS("getPimItems"),

  /**
   * Pairs this computer with the device, as BlackBerry Link does: the device lists it in Device
   * Connections. Changes the device, so it runs behind the PIN guard.
   */
  PAIR_DEVICE("pairDevice"),

  /**
   * Asks the device to sign a certificate for this computer, for the certificate login on port
   * 8443, and keeps it with its key. Changes the device, so it runs behind the PIN guard.
   */
  GET_AUTHENTICATION_CERTIFICATE("getAuthenticationCertificate"),

  /** Scans for devices; needs no session. */
  LIST_DEVICES("listDevices"),

  /**
   * Reads what a bar says about its package, and optionally checks it against BlackBerry's rules.
   * Needs neither a session nor a device.
   */
  INSPECT_PACKAGE("inspectPackage"),

  /**
   * Installs applications from bars on the device. Changes the device, so it is never repeated
   * after a dropped connection.
   */
  INSTALL_PACKAGES("installPackages"),

  /**
   * Removes applications from the device by their package ids. Changes the device, and removes the
   * applications' data; never repeated after a dropped connection.
   */
  UNINSTALL_PACKAGES("uninstallPackages"),

  /** Says what a certificate file holds and how long it has left. Needs no session or device. */
  INSPECT_CERTIFICATE("inspectCertificate");

  /** The package that a replayed installation is attributed to; what the recording says is kept. */
  private static final BarInfo REPLAY_BAR =
      new BarInfo(
          Path.of("replay.bar"),
          1024,
          BarKind.APPLICATION,
          "application",
          "replay.package",
          "Replay",
          "1.0.0.0",
          "armle-v7",
          "1.5",
          false,
          false);

  /** The workstation identifier that replayed recordings are attributed to. */
  private static final String REPLAY_WORKSTATION = "replay";

  /** The port that takes client certificates, which a new identity is tried against. */
  private static final int CERTIFICATE_PORT = 8443;

  private final String wireName;

  LinkMethod(String wireName) {
    this.wireName = wireName;
  }

  static Optional<LinkMethod> byWireName(String name) {
    return Arrays.stream(values()).filter(method -> method.wireName.equals(name)).findFirst();
  }

  /**
   * A removal to replay a recording with: one package, or as many as the argument says (1 to 9), as
   * in {@code uninstallPackages.2}. The package ids are made up; what the recording says is kept.
   */
  private static Optional<DeviceCommand<?>> replayedRemoval(String argument) {
    var text = argument != null ? argument : "1";
    if (!text.matches("[1-9]")) {
      return Optional.empty();
    }
    var count = Integer.parseInt(text);
    var packageIds =
        count == 1
            ? List.of("replay.package")
            : IntStream.rangeClosed(1, count)
                .mapToObj(number -> "replay.package." + number)
                .toList();
    return Optional.of(new UninstallPackages(packageIds, false));
  }

  /**
   * Reads the parameters of {@code uninstallPackages}; a bad one is reported before anything is
   * sent.
   */
  private static UninstallPackages uninstallPackages(RequestParams params) {
    return new UninstallPackages(params.requireStrings("packageIds"), params.flag("check", true));
  }

  /** Reads the parameters of {@code getPimItems}; a bad one is reported before anything is sent. */
  private static GetPimItems getPimItems(RequestParams params) {
    return new GetPimItems(
        params.requireString("workstationId"),
        PimService.of(params.requireString("service")),
        new PimFilters(
            params.optionalString("updatedTime").orElse(null),
            params.optionalInteger("limit", 1, Integer.MAX_VALUE),
            params.flag("dirtyOnly"),
            params.flag("localOnly"),
            params.optionalInteger("offset", 0, Integer.MAX_VALUE)));
  }

  String wireName() {
    return wireName;
  }

  /**
   * The command to replay recorded answers through, or empty if the method has none.
   *
   * @param argument what follows the method name after a dot in {@code -parse}, for example the
   *     service of {@code getPimItems.contacts}; only {@link #GET_PIM_ITEMS} takes one, and needs
   *     it
   * @throws com.jbraddockm.core.error.InvalidArgumentException if the argument is not accepted
   */
  Optional<DeviceCommand<?>> replayCommand(String argument) {
    return switch (this) {
      case GET_DEVICE_PROPERTIES ->
          argument == null ? Optional.of(new GetDeviceProperties()) : Optional.empty();
      case GET_PIN -> argument == null ? Optional.of(new GetPin()) : Optional.empty();
      case LIST_PIM_ACCOUNTS ->
          argument == null ? Optional.of(new ListPimAccounts()) : Optional.empty();
      case FIND_PIM_ACCOUNT ->
          argument == null ? Optional.of(new FindPimAccount(REPLAY_WORKSTATION)) : Optional.empty();
      case PAIR_DEVICE ->
          argument == null ? Optional.of(new PairWithDevice("REPLAY", "x")) : Optional.empty();
      case GET_AUTHENTICATION_CERTIFICATE ->
          argument == null
              ? Optional.of(new GetAuthenticationCertificate("replay"))
              : Optional.empty();
      case GET_PIM_ITEMS ->
          argument != null
              ? Optional.of(
                  new GetPimItems(REPLAY_WORKSTATION, PimService.of(argument), PimFilters.none()))
              : Optional.empty();
      case INSTALL_PACKAGES ->
          argument == null
              ? Optional.of(new InstallPackages(List.of(REPLAY_BAR), Duration.ZERO))
              : Optional.empty();
      case UNINSTALL_PACKAGES -> replayedRemoval(argument);
      case GET_SMB_CREDENTIALS, LIST_DEVICES, INSPECT_PACKAGE, INSPECT_CERTIFICATE ->
          Optional.empty();
    };
  }

  CommandSpec<DeviceSession> spec(
      DeviceScanner scanner, Pairing pairing, CertificateRetrieval retrieval) {
    return guarded(specFor(scanner, pairing, retrieval));
  }

  /**
   * Wraps a session-scoped specification so the command is refused before it runs when the
   * session's path cannot reach the program the method needs. A standalone method has no session
   * and is returned unchanged.
   */
  private CommandSpec<DeviceSession> guarded(CommandSpec<DeviceSession> spec) {
    if (spec instanceof CommandSpec.SessionScoped<DeviceSession>(var name, var factory)) {
      return new CommandSpec.SessionScoped<>(
          name,
          params -> {
            var command = factory.apply(params);
            return (session, reply) ->
                EndpointReachability.refusalFor(this, session.target().path())
                    .ifPresentOrElse(reply::fail, () -> command.run(session, reply));
          });
    }
    return spec;
  }

  private CommandSpec<DeviceSession> specFor(
      DeviceScanner scanner, Pairing pairing, CertificateRetrieval retrieval) {
    return switch (this) {
      case GET_DEVICE_PROPERTIES -> onSession(_ -> new GetDeviceProperties());
      case GET_PIN -> onSession(_ -> new GetPin());
      case LIST_PIM_ACCOUNTS -> onSession(_ -> new ListPimAccounts());
      case FIND_PIM_ACCOUNT ->
          onSession(params -> new FindPimAccount(params.requireString("workstationId")));
      case GET_PIM_ITEMS -> onSession(LinkMethod::getPimItems);
      case UNINSTALL_PACKAGES -> onSession(LinkMethod::uninstallPackages);
      case PAIR_DEVICE ->
          new CommandSpec.SessionScoped<>(wireName, params -> pairDevice(params, pairing));
      case GET_AUTHENTICATION_CERTIFICATE ->
          new CommandSpec.SessionScoped<>(
              wireName, params -> getAuthenticationCertificate(params, retrieval));
      case INSPECT_CERTIFICATE ->
          new CommandSpec.Standalone<>(
              wireName,
              params -> {
                var file = params.requirePath("file");
                return reply ->
                    reply.complete(
                        wireName, InspectCertificate.inspect(file, Clock.systemUTC()), false);
              });
      case INSTALL_PACKAGES ->
          new CommandSpec.SessionScoped<>(
              wireName,
              params -> {
                var names = params.requireStrings("files");
                var includeRaw = params.flag("raw");
                return (session, reply) ->
                    // The bars are read here, on the session's own thread, not while the request
                    // is read: the manifests have to be opened.
                    reply.complete(
                        wireName,
                        switch (BarSelection.of(names)) {
                          case BarSelection.Refused(var error) -> CommandOutcome.failed(error);
                          case BarSelection.Selected(var bars) -> {
                            try {
                              yield session.execute(InstallPackages.of(bars), reply);
                            } catch (InvalidArgumentException invalid) {
                              yield CommandOutcome.<InstallResult>failed(invalid.toServiceError());
                            }
                          }
                        },
                        includeRaw);
              });
      case GET_SMB_CREDENTIALS ->
          new CommandSpec.SessionScoped<>(
              wireName,
              _ ->
                  (session, reply) ->
                      session
                          .smbCredentials()
                          .ifPresentOrElse(
                              credentials -> {
                                reply.result(wireName, credentials.toJson(), null);
                                reply.succeed();
                              },
                              () ->
                                  reply.fail(
                                      new ServiceError(
                                          CoreErrorCode.COMMAND_FAILED,
                                          "The device did not hand out file share credentials"))));
      case LIST_DEVICES ->
          new CommandSpec.Standalone<>(
              wireName,
              _ ->
                  reply ->
                      reply.complete(
                          wireName,
                          CommandOutcome.succeeded(
                              DeviceDiscovery.discover(
                                  scanner, device -> reply.event("device", device.toJson()))),
                          false));
      case INSPECT_PACKAGE ->
          new CommandSpec.Standalone<>(
              wireName,
              params -> {
                var file = params.requirePath("file");
                var validate = params.flag("validate");
                return reply ->
                    reply.complete(wireName, InspectPackage.inspect(file, validate), false);
              });
    };
  }

  /**
   * A method that runs one device command on the session. The parameters are read, and the command
   * built, before anything is queued, so a bad parameter is answered at once.
   */
  private SessionCommand<DeviceSession> pairDevice(RequestParams params, Pairing pairing) {
    var request =
        new PairingRequest(
            params.optionalString("confirmPin").orElse(null),
            params
                .optionalPath("devicesDirectory")
                .orElseThrow(() -> new InvalidArgumentException("devicesDirectory", "is required")),
            params.flag("replace"));
    var includeRaw = params.flag("raw");
    return (session, reply) ->
        reply.complete(wireName, pairing.run(session, request, reply), includeRaw);
  }

  private SessionCommand<DeviceSession> getAuthenticationCertificate(
      RequestParams params, CertificateRetrieval retrieval) {
    var request =
        new CertificateRetrievalRequest(
            params.optionalString("confirmPin").orElse(null),
            params
                .optionalPath("devicesDirectory")
                .orElseThrow(() -> new InvalidArgumentException("devicesDirectory", "is required")),
            params.optionalString("hostName").orElse(null),
            params.flag("replace"),
            params.flag("verify", true),
            params.integer("verifyPort", CERTIFICATE_PORT, 1, 65_535));
    var includeRaw = params.flag("raw");
    return (session, reply) ->
        reply.complete(wireName, retrieval.run(session, request, reply), includeRaw);
  }

  private <R extends CommandResult> CommandSpec<DeviceSession> onSession(
      Function<RequestParams, DeviceCommand<R>> commandFactory) {
    return new CommandSpec.SessionScoped<>(
        wireName,
        params -> {
          var command = commandFactory.apply(params);
          var includeRaw = params.flag("raw");
          return (session, reply) ->
              reply.complete(wireName, session.execute(command, reply), includeRaw);
        });
  }
}
