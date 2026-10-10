package com.jbraddockm.output;

import java.util.Map;
import java.util.Optional;

/**
 * The parsers for the commands of {@code blackberry-deploy}.
 *
 * <p>Each constant is stateless and safe to share between threads.
 */
public final class OutputParsers {

  /** Parser for {@code -listInstalledApps}. */
  public static final OutputParser<InstalledApps> INSTALLED_APPS = new InstalledAppsParser();

  /** Parser for {@code -listDeviceInfo}. */
  public static final OutputParser<DeviceInfo> DEVICE_INFO = new DeviceInfoParser();

  /** Parser for {@code -installApp}, with or without {@code -launchApp}. */
  public static final OutputParser<InstallResult> INSTALL_APP = new InstallParser();

  /** Parser for {@code -launchApp}. */
  public static final OutputParser<Verdict> LAUNCH_APP = new VerdictParser("Launch");

  /** Parser for {@code -terminateApp}. */
  public static final OutputParser<Verdict> TERMINATE_APP = new VerdictParser("Terminate");

  /** Parser for {@code -isAppRunning}. */
  public static final OutputParser<AppRunState> APP_RUNNING = new RunStateParser();

  /** Parser for {@code -verifyApp}. */
  public static final OutputParser<PackageInfo> VERIFY_APP = new PackageInfoParser();

  /** Parser for {@code -uninstallApp}. */
  public static final OutputParser<PackageInfo> UNINSTALL_APP = new PackageInfoParser();

  /** Parser for {@code -devices}. */
  public static final OutputParser<DetectedDevices> DETECTED_DEVICES = new DeviceListParser();

  /**
   * For commands whose output has no structure worth parsing; always yields {@link
   * NoData#INSTANCE}.
   */
  public static final OutputParser<NoData> NO_DATA = _ -> NoData.INSTANCE;

  private static final Map<String, OutputParser<? extends CommandResult>> BY_COMMAND =
      Map.ofEntries(
          Map.entry("listInstalledApps", INSTALLED_APPS),
          Map.entry("listDeviceInfo", DEVICE_INFO),
          Map.entry("installApp", INSTALL_APP),
          Map.entry("launchApp", LAUNCH_APP),
          Map.entry("terminateApp", TERMINATE_APP),
          Map.entry("isAppRunning", APP_RUNNING),
          Map.entry("verifyApp", VERIFY_APP),
          Map.entry("uninstallApp", UNINSTALL_APP),
          Map.entry("test", NO_DATA),
          Map.entry("getFile", NO_DATA),
          Map.entry("putFile", NO_DATA),
          Map.entry("installDebugToken", NO_DATA),
          Map.entry("listDevices", DETECTED_DEVICES));

  private OutputParsers() {}

  /**
   * Looks a parser up by the name of the command, without the leading dash.
   *
   * @param commandName for example {@code listInstalledApps}
   * @return the parser, or empty if the command is unknown
   */
  public static Optional<OutputParser<? extends CommandResult>> forCommand(String commandName) {
    return Optional.ofNullable(BY_COMMAND.get(commandName));
  }
}
