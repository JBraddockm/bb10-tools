package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.daemon.CommandSpec;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.actions.GetPin;
import com.jbraddockm.link.discovery.DeviceDiscovery;
import com.jbraddockm.link.discovery.DeviceScanner;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.session.DeviceCommand;
import com.jbraddockm.link.session.DeviceSession;
import java.util.Arrays;
import java.util.Optional;

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

  /** Scans for devices; needs no session. */
  LIST_DEVICES("listDevices");

  private final String wireName;

  LinkMethod(String wireName) {
    this.wireName = wireName;
  }

  String wireName() {
    return wireName;
  }

  static Optional<LinkMethod> byWireName(String name) {
    return Arrays.stream(values()).filter(method -> method.wireName.equals(name)).findFirst();
  }

  /** The command to replay recorded answers through, or empty if the method has none. */
  Optional<DeviceCommand<?>> replayCommand() {
    return switch (this) {
      case GET_DEVICE_PROPERTIES -> Optional.of(new GetDeviceProperties());
      case GET_PIN -> Optional.of(new GetPin());
      case GET_SMB_CREDENTIALS, LIST_DEVICES -> Optional.empty();
    };
  }

  CommandSpec<DeviceSession> spec(DeviceScanner scanner) {
    return switch (this) {
      case GET_DEVICE_PROPERTIES -> onSession(new GetDeviceProperties());
      case GET_PIN -> onSession(new GetPin());
      case GET_SMB_CREDENTIALS ->
          new CommandSpec.SessionScoped<>(
              wireName,
              params ->
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
              params ->
                  reply ->
                      reply.complete(
                          wireName,
                          CommandOutcome.succeeded(
                              DeviceDiscovery.discover(
                                  scanner, device -> reply.event("device", device.toJson()))),
                          false));
    };
  }

  private <R extends CommandResult> CommandSpec<DeviceSession> onSession(DeviceCommand<R> command) {
    return new CommandSpec.SessionScoped<>(
        wireName,
        params -> {
          var includeRaw = params.flag("raw");
          return (session, reply) ->
              reply.complete(wireName, session.execute(command, reply), includeRaw);
        });
  }
}
