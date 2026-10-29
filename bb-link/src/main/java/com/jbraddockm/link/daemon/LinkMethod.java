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
import com.jbraddockm.link.endpoint.actions.GetPin;
import com.jbraddockm.link.endpoint.backup.BackupCategory;
import com.jbraddockm.link.endpoint.backup.BackupRequest;
import com.jbraddockm.link.endpoint.backup.DeviceBackup;
import com.jbraddockm.link.endpoint.backup.DeviceRestore;
import com.jbraddockm.link.endpoint.backup.PackageType;
import com.jbraddockm.link.endpoint.backup.RestoreRequest;
import com.jbraddockm.link.endpoint.characteristics.GetDeviceCharacteristics;
import com.jbraddockm.link.endpoint.discovery.DeviceDiscovery;
import com.jbraddockm.link.endpoint.discovery.DeviceScanner;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrievalRequest;
import com.jbraddockm.link.endpoint.pairing.GetAuthenticationCertificate;
import com.jbraddockm.link.endpoint.pairing.InspectCertificate;
import com.jbraddockm.link.endpoint.pairing.PairWithDevice;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.endpoint.pairing.PairingRequest;
import com.jbraddockm.link.endpoint.pairing.RemovePairing;
import com.jbraddockm.link.endpoint.pairing.RenewPairing;
import com.jbraddockm.link.endpoint.pairing.RenewRequest;
import com.jbraddockm.link.endpoint.pimsync.PimFilters;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.account.CreatePimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.DeletePimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.FindPimAccount;
import com.jbraddockm.link.endpoint.pimsync.account.ListPimAccounts;
import com.jbraddockm.link.endpoint.pimsync.account.SetDefaultCalendar;
import com.jbraddockm.link.endpoint.pimsync.account.UpdatePimAccount;
import com.jbraddockm.link.endpoint.pimsync.item.DeletePimItem;
import com.jbraddockm.link.endpoint.pimsync.item.FinishPimSync;
import com.jbraddockm.link.endpoint.pimsync.item.GetPimItems;
import com.jbraddockm.link.endpoint.pimsync.item.PutPimItem;
import com.jbraddockm.link.endpoint.pimsync.item.SummarizePimSync;
import com.jbraddockm.link.endpoint.pimsync.photo.GetPimPhoto;
import com.jbraddockm.link.endpoint.pimsync.photo.PutPimPhoto;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import com.jbraddockm.link.endpoint.update.BarSelection;
import com.jbraddockm.link.endpoint.update.InspectPackage;
import com.jbraddockm.link.endpoint.update.InstallPackages;
import com.jbraddockm.link.endpoint.update.InstallResult;
import com.jbraddockm.link.endpoint.update.UninstallPackages;
import com.jbraddockm.link.transport.cgi.ConnectionPath;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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

  /** Reads the device's characteristics (identity, PIN, status) from {@code discovery.cgi}. */
  GET_DEVICE_CHARACTERISTICS("getDeviceCharacteristics"),

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

  /** Registers a workstation as a PIM account on the device. Changes the device. */
  CREATE_PIM_ACCOUNT("createPimAccount"),

  /** Updates the account of a workstation. Changes the device. */
  UPDATE_PIM_ACCOUNT("updatePimAccount"),

  /** Removes the account of a workstation. Changes the device. */
  DELETE_PIM_ACCOUNT("deletePimAccount"),

  /** Sets the account's default calendar object. Changes the device. */
  SET_DEFAULT_CALENDAR("setDefaultCalendar"),

  /** Removes one item of a service from a workstation's account. Changes the device. */
  DELETE_PIM_ITEM("deletePimItem"),

  /** Creates or updates one item of a service in a workstation's account. Changes the device. */
  PUT_PIM_ITEM("putPimItem"),

  /** Opens a sync round for a workstation's account and asks what changed. Part of a sync round. */
  SUMMARIZE_PIM_SYNC("summarizePimSync"),

  /** Closes a sync round, submitting the reconciled result. Changes the device. */
  FINISH_PIM_SYNC("finishPimSync"),

  /** Fetches one contact photo from the device and streams it to a file. */
  GET_PIM_PHOTO("getPimPhoto"),

  /** Sends one contact photo to the device. Changes the device. */
  PUT_PIM_PHOTO("putPimPhoto"),

  /**
   * Pairs this computer with the device, as BlackBerry Link does: the device lists it in Device
   * Connections. Changes the device, so it runs behind the PIN guard.
   */
  PAIR_DEVICE("pairDevice"),

  /**
   * Forgets a device pairing on this computer by deleting its folder (keys, certificates and
   * metadata). Purely local — it sends nothing to the device, which keeps the pairing until it is
   * removed there. Needs no session or device.
   */
  REMOVE_PAIRING("removePairing"),

  /**
   * Asks the device to sign a certificate for this computer, for the certificate login on port
   * 8443, and keeps it with its key. Changes the device, so it runs behind the PIN guard.
   */
  GET_AUTHENTICATION_CERTIFICATE("getAuthenticationCertificate"),

  /**
   * Renews everything kept for a paired device: re-enrols the pairing authority under the known
   * identifier (reaffirming it, or re-minting it when the certificates are gone) and refreshes the
   * port 8443 login certificate. Needs a pairing already on disk. Changes the device, so it runs
   * behind the PIN guard, and it reaches {@code pairing.cgi}, so only over USB or the tunnel.
   */
  RENEW_PAIRING("renewPairing"),

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
  INSPECT_CERTIFICATE("inspectCertificate"),

  /**
   * Backs the device up into a {@code .bbb} file. Reaches {@code backup.cgi}, so only over USB or
   * the tunnel. The device must already hold a backup-and-restore key for the data to stream.
   */
  BACKUP_DEVICE("backupDevice"),

  /**
   * Restores a {@code .bbb} file onto the device, which resets to apply it. Destructive: it sends
   * nothing unless the request confirms it. Reaches {@code backup.cgi}, so only over USB or the
   * tunnel.
   */
  RESTORE_DEVICE("restoreDevice");

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
      case GET_DEVICE_CHARACTERISTICS ->
          argument == null ? Optional.of(new GetDeviceCharacteristics()) : Optional.empty();
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
      case CREATE_PIM_ACCOUNT ->
          argument == null
              ? Optional.of(new CreatePimAccount(REPLAY_WORKSTATION, "Replay"))
              : Optional.empty();
      case UPDATE_PIM_ACCOUNT ->
          argument == null
              ? Optional.of(new UpdatePimAccount(REPLAY_WORKSTATION, "Replay"))
              : Optional.empty();
      case DELETE_PIM_ACCOUNT ->
          argument == null
              ? Optional.of(new DeletePimAccount(REPLAY_WORKSTATION))
              : Optional.empty();
      case SET_DEFAULT_CALENDAR ->
          argument == null
              ? Optional.of(new SetDefaultCalendar(REPLAY_WORKSTATION, "replay.object"))
              : Optional.empty();
      case DELETE_PIM_ITEM ->
          argument != null
              ? Optional.of(
                  new DeletePimItem(REPLAY_WORKSTATION, PimService.of(argument), "replay.object"))
              : Optional.empty();
      case PUT_PIM_ITEM ->
          argument != null
              ? Optional.of(
                  new PutPimItem(
                      REPLAY_WORKSTATION, PimService.of(argument), Map.of("replay", "item")))
              : Optional.empty();
      case SUMMARIZE_PIM_SYNC ->
          argument != null
              ? Optional.of(new SummarizePimSync(REPLAY_WORKSTATION, PimService.of(argument)))
              : Optional.empty();
      case FINISH_PIM_SYNC ->
          argument != null
              ? Optional.of(
                  new FinishPimSync(
                      REPLAY_WORKSTATION, PimService.of(argument), Map.of("replay", "item")))
              : Optional.empty();
      case GET_PIM_PHOTO ->
          argument == null
              ? Optional.of(
                  new GetPimPhoto(
                      REPLAY_WORKSTATION, "replay.path", Path.of("replay.jpg"), Optional.empty()))
              : Optional.empty();
      case PUT_PIM_PHOTO ->
          argument == null
              ? Optional.of(
                  new PutPimPhoto(
                      REPLAY_WORKSTATION, "replay.account", "replay.object", Path.of("replay.jpg")))
              : Optional.empty();
      case INSTALL_PACKAGES ->
          argument == null
              ? Optional.of(new InstallPackages(List.of(REPLAY_BAR), Duration.ZERO))
              : Optional.empty();
      case UNINSTALL_PACKAGES -> replayedRemoval(argument);
      case BACKUP_DEVICE ->
          argument == null ? Optional.of(DeviceBackup.replayCommand()) : Optional.empty();
      case RESTORE_DEVICE ->
          argument == null ? Optional.of(DeviceRestore.replayCommand()) : Optional.empty();
      case GET_SMB_CREDENTIALS,
          LIST_DEVICES,
          INSPECT_PACKAGE,
          INSPECT_CERTIFICATE,
          REMOVE_PAIRING,
          RENEW_PAIRING ->
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
      case GET_DEVICE_CHARACTERISTICS -> onSession(_ -> new GetDeviceCharacteristics());
      case GET_PIN -> onSession(_ -> new GetPin());
      case LIST_PIM_ACCOUNTS -> onSession(_ -> new ListPimAccounts());
      case FIND_PIM_ACCOUNT ->
          onSession(params -> new FindPimAccount(params.requireString("workstationId")));
      case GET_PIM_ITEMS -> onSession(LinkMethod::getPimItems);
      case CREATE_PIM_ACCOUNT ->
          onSession(
              params ->
                  new CreatePimAccount(
                      params.requireString("workstationId"),
                      params.requireString("workstationName")));
      case UPDATE_PIM_ACCOUNT ->
          onSession(
              params ->
                  new UpdatePimAccount(
                      params.requireString("workstationId"),
                      params.requireString("workstationName")));
      case DELETE_PIM_ACCOUNT ->
          onSession(params -> new DeletePimAccount(params.requireString("workstationId")));
      case SET_DEFAULT_CALENDAR ->
          onSession(
              params ->
                  new SetDefaultCalendar(
                      params.requireString("workstationId"), params.requireString("objectId")));
      case DELETE_PIM_ITEM ->
          onSession(
              params ->
                  new DeletePimItem(
                      params.requireString("workstationId"),
                      PimService.of(params.requireString("service")),
                      params.requireString("objectId")));
      case PUT_PIM_ITEM ->
          onSession(
              params ->
                  new PutPimItem(
                      params.requireString("workstationId"),
                      PimService.of(params.requireString("service")),
                      params.requireObjectMap("item")));
      case SUMMARIZE_PIM_SYNC ->
          onSession(
              params ->
                  new SummarizePimSync(
                      params.requireString("workstationId"),
                      PimService.of(params.requireString("service"))));
      case FINISH_PIM_SYNC ->
          onSession(
              params ->
                  new FinishPimSync(
                      params.requireString("workstationId"),
                      PimService.of(params.requireString("service")),
                      params.requireObjectMap("payload")));
      case GET_PIM_PHOTO ->
          onSession(
              params ->
                  new GetPimPhoto(
                      params.requireString("workstationId"),
                      params.requireString("path"),
                      params.requirePath("destination"),
                      params.optionalString("updatedTime")));
      case PUT_PIM_PHOTO ->
          onSession(
              params ->
                  new PutPimPhoto(
                      params.requireString("workstationId"),
                      params.requireString("accountId"),
                      params.requireString("objectId"),
                      params.requirePath("file")));
      case UNINSTALL_PACKAGES -> onSession(LinkMethod::uninstallPackages);
      case PAIR_DEVICE ->
          new CommandSpec.SessionScoped<>(wireName, params -> pairDevice(params, pairing));
      case GET_AUTHENTICATION_CERTIFICATE ->
          new CommandSpec.SessionScoped<>(
              wireName, params -> getAuthenticationCertificate(params, retrieval));
      case RENEW_PAIRING ->
          new CommandSpec.SessionScoped<>(
              wireName, params -> renewPairing(params, new RenewPairing(pairing, retrieval)));
      case INSPECT_CERTIFICATE ->
          new CommandSpec.Standalone<>(
              wireName,
              params -> {
                var file = params.requirePath("file");
                return reply ->
                    reply.complete(
                        wireName, InspectCertificate.inspect(file, Clock.systemUTC()), false);
              });
      case REMOVE_PAIRING ->
          new CommandSpec.Standalone<>(
              wireName,
              params -> {
                var devices = params.requirePath("devicesDirectory");
                var pin = params.requireString("pin");
                return reply -> reply.complete(wireName, RemovePairing.remove(devices, pin), false);
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
                              var command = InstallPackages.of(bars);
                              if (command.isFirmware()
                                  && session.connectionPath() != ConnectionPath.USB) {
                                yield CommandOutcome.<InstallResult>failed(
                                    new ServiceError(
                                        CoreErrorCode.COMMAND_FAILED,
                                        "Installing firmware is only available over the USB"
                                            + " connection; connect the device by USB and try"
                                            + " again"));
                              }
                              yield session.execute(command, reply);
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
      case BACKUP_DEVICE ->
          new CommandSpec.SessionScoped<>(
              wireName,
              params -> {
                var request =
                    new BackupRequest(
                        params.requirePath("outputFile"),
                        BackupCategory.select(params.optionalStrings("categories")),
                        PackageType.select(params.optionalStrings("packageTypes")));
                var includeRaw = params.flag("raw");
                return (session, reply) ->
                    reply.complete(
                        wireName, new DeviceBackup().run(session, request, reply), includeRaw);
              });
      case RESTORE_DEVICE ->
          new CommandSpec.SessionScoped<>(
              wireName,
              params -> {
                var request =
                    new RestoreRequest(
                        params.requirePath("inputFile"),
                        BackupCategory.select(params.optionalStrings("categories")),
                        params.flag("confirm"));
                var includeRaw = params.flag("raw");
                return (session, reply) ->
                    reply.complete(
                        wireName, new DeviceRestore().run(session, request, reply), includeRaw);
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
            params.flag("renew"));
    var includeRaw = params.flag("raw");
    return (session, reply) ->
        reply.complete(wireName, pairing.run(session, request, reply), includeRaw);
  }

  private SessionCommand<DeviceSession> renewPairing(RequestParams params, RenewPairing renew) {
    var request =
        new RenewRequest(
            params.optionalString("confirmPin").orElse(null),
            params
                .optionalPath("devicesDirectory")
                .orElseThrow(() -> new InvalidArgumentException("devicesDirectory", "is required")),
            params.optionalString("hostName").orElse(null),
            params.flag("verify", true),
            params.integer("verifyPort", CERTIFICATE_PORT, 1, 65_535));
    var includeRaw = params.flag("raw");
    return (session, reply) ->
        reply.complete(wireName, renew.run(session, request, reply), includeRaw);
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
