package com.jbraddockm.daemon;

import com.jbraddockm.device.DeviceCommand;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/** The methods of the protocol, in the order they are announced in the {@code hello} event. */
enum Method {
  CONNECT("connect"),
  DISCONNECT("disconnect"),
  SHUTDOWN("shutdown"),
  TEST("test", _ -> new DeviceCommand.TestConnection()),
  LIST_INSTALLED_APPS("listInstalledApps", _ -> new DeviceCommand.ListInstalledApps()),
  LIST_DEVICE_INFO("listDeviceInfo", _ -> new DeviceCommand.ListDeviceInfo()),
  INSTALL_APP(
      "installApp",
      params ->
          new DeviceCommand.InstallApp(
              params.requirePath("bar"),
              params.flag("launch"),
              params.flag("noSync"),
              params.flag("devMode"),
              params.optionalString("debugHost").orElse(null),
              params.flag("debugNative"),
              params.optionalPath("map").orElse(null))),
  LAUNCH_APP("launchApp", params -> new DeviceCommand.LaunchApp(params.packageRef())),
  TERMINATE_APP("terminateApp", params -> new DeviceCommand.TerminateApp(params.packageRef())),
  IS_APP_RUNNING("isAppRunning", params -> new DeviceCommand.IsAppRunning(params.packageRef())),
  UNINSTALL_APP("uninstallApp", params -> new DeviceCommand.UninstallApp(params.packageRef())),
  VERIFY_APP("verifyApp", params -> new DeviceCommand.VerifyApp(params.packageRef())),
  GET_FILE(
      "getFile",
      params ->
          new DeviceCommand.GetFile(
              params.packageRef(),
              params.requireString("path"),
              params.optionalPath("saveAs").orElse(null))),
  PUT_FILE(
      "putFile",
      params ->
          new DeviceCommand.PutFile(
              params.packageRef(), params.requirePath("localPath"), params.requireString("path"))),
  INSTALL_DEBUG_TOKEN(
      "installDebugToken",
      params -> new DeviceCommand.InstallDebugToken(params.requirePath("token"))),
  LIST_DEVICES("listDevices", _ -> new DeviceCommand.ListDevices());

  private final String wireName;
  private final Function<RequestParams, DeviceCommand<?>> commandFactory;

  /** A method that manages sessions and runs no device command. */
  Method(String wireName) {
    this(wireName, null);
  }

  Method(String wireName, Function<RequestParams, DeviceCommand<?>> commandFactory) {
    this.wireName = wireName;
    this.commandFactory = commandFactory;
  }

  static Optional<Method> byWireName(String name) {
    return Arrays.stream(values()).filter(method -> method.wireName.equals(name)).findFirst();
  }

  static List<String> wireNames() {
    return Arrays.stream(values()).map(Method::wireName).toList();
  }

  String wireName() {
    return wireName;
  }

  /**
   * Builds the device command this method stands for.
   *
   * @throws com.jbraddockm.device.InvalidArgumentException if the parameters are not acceptable
   * @throws IllegalStateException if this method manages sessions
   */
  DeviceCommand<?> command(RequestParams params) {
    if (commandFactory == null) {
      throw new IllegalStateException(wireName + " is not a device command");
    }
    return commandFactory.apply(params);
  }
}
