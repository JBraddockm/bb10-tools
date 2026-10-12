package com.jbraddockm.device;

import com.jbraddockm.output.AppRunState;
import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.DetectedDevices;
import com.jbraddockm.output.DeviceInfo;
import com.jbraddockm.output.FileDownload;
import com.jbraddockm.output.FileUpload;
import com.jbraddockm.output.InstallResult;
import com.jbraddockm.output.InstalledApps;
import com.jbraddockm.output.NoData;
import com.jbraddockm.output.OutputParser;
import com.jbraddockm.output.OutputParsers;
import com.jbraddockm.output.PackageInfo;
import com.jbraddockm.output.Verdict;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A command that can be run on a device. Each variant carries exactly the parameters its command
 * needs and validates them when it is created, so an instance is always ready to run.
 *
 * @param <T> the type of result the command produces
 */
public sealed interface DeviceCommand<T extends CommandResult> {

  /**
   * Returns the command-line arguments of the deploy tool for this command, without the device
   * address and password, which the session adds.
   *
   * @return the arguments, in order
   */
  List<String> arguments();

  /**
   * Returns the parser for this command's output.
   *
   * @return the parser
   */
  OutputParser<T> parser();

  /**
   * Tells whether running the command twice is harmless. Only such commands are retried
   * automatically after a dropped connection.
   *
   * @return {@code true} for commands that only read
   */
  default boolean readOnly() {
    return false;
  }

  /**
   * Tells whether the command talks to a device.
   *
   * @return {@code false} for commands that need no login, such as listing the devices found on the
   *     network
   */
  default boolean requiresDevice() {
    return true;
  }

  /**
   * Tells whether the Deploy tool prints part of this command's output with {@code System.out}
   * instead of the stream the session gives it. The session then captures {@code System.out} for
   * the duration of the command (see {@link StandardOutputCapture}).
   *
   * @return {@code true} if some output would otherwise be lost
   */
  default boolean printsToStandardOutput() {
    return false;
  }

  /** Checks that the device is reachable and accepts the login. */
  record TestConnection() implements DeviceCommand<NoData> {
    @Override
    public List<String> arguments() {
      return List.of("-test");
    }

    @Override
    public OutputParser<NoData> parser() {
      return OutputParsers.NO_DATA;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /** Lists the applications installed on the device. */
  record ListInstalledApps() implements DeviceCommand<InstalledApps> {
    @Override
    public List<String> arguments() {
      return List.of("-listInstalledApps");
    }

    @Override
    public OutputParser<InstalledApps> parser() {
      return OutputParsers.INSTALLED_APPS;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /** Reads the device's properties. */
  record ListDeviceInfo() implements DeviceCommand<DeviceInfo> {
    @Override
    public List<String> arguments() {
      return List.of("-listDeviceInfo");
    }

    @Override
    public OutputParser<DeviceInfo> parser() {
      return OutputParsers.DEVICE_INFO;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /**
   * Installs an application, optionally launching it.
   *
   * @param bar the BAR file to install
   * @param launch whether to launch the app after installing it
   * @param noSync whether to disable the incremental install and always send the whole package
   * @param devMode whether to pass {@code -devMode}
   * @param debugHost the host of a debugger to attach, or {@code null}
   * @param debugNative whether to hold a native app at start-up so a native debugger can attach
   * @param map an experimental file-mapping file, or {@code null}
   */
  record InstallApp(
      Path bar,
      boolean launch,
      boolean noSync,
      boolean devMode,
      String debugHost,
      boolean debugNative,
      Path map)
      implements DeviceCommand<InstallResult> {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    public InstallApp {
      Objects.requireNonNull(bar, "bar");
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-installApp")
          .addIf(launch, "-launchApp")
          .add(
              "-package",
              bar.toString()) // explicit flag: a path starting with '-' is not an option
          .addIf(noSync, "-noSync")
          .addIf(devMode, "-devMode")
          .addIf(debugNative, "-debugNative")
          .addOption("-debugHost", debugHost)
          .addOption("-map", map)
          .build();
    }

    @Override
    public OutputParser<InstallResult> parser() {
      return OutputParsers.INSTALL_APP;
    }
  }

  /**
   * Launches an installed application.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record LaunchApp(PackageRef app) implements DeviceCommand<Verdict> {

    /**
     * Creates the command.
     *
     * @throws InvalidArgumentException if the reference lacks the package name
     */
    public LaunchApp {
      app.requireName();
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-launchApp").addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<Verdict> parser() {
      return OutputParsers.LAUNCH_APP;
    }
  }

  /**
   * Terminates a running application. Terminating one that is already stopped succeeds.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record TerminateApp(PackageRef app) implements DeviceCommand<Verdict> {

    /**
     * Creates the command.
     *
     * @throws InvalidArgumentException if the reference lacks the package name
     */
    public TerminateApp {
      app.requireName();
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-terminateApp").addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<Verdict> parser() {
      return OutputParsers.TERMINATE_APP;
    }
  }

  /**
   * Asks whether an application is running. The device answers {@code false} for an app that is not
   * installed.
   *
   * @param app the application; a name-and-id reference must carry the name
   */
  record IsAppRunning(PackageRef app) implements DeviceCommand<AppRunState> {

    /**
     * Creates the command.
     *
     * @throws InvalidArgumentException if the reference lacks the package name
     */
    public IsAppRunning {
      app.requireName();
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-isAppRunning").addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<AppRunState> parser() {
      return OutputParsers.APP_RUNNING;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /**
   * Uninstalls an application. A running one is terminated first.
   *
   * @param app the application
   */
  record UninstallApp(PackageRef app) implements DeviceCommand<PackageInfo> {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code app} is {@code null}
     */
    public UninstallApp {
      Objects.requireNonNull(app, "app");
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-uninstallApp").addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<PackageInfo> parser() {
      return OutputParsers.UNINSTALL_APP;
    }
  }

  /**
   * Verifies an installed application.
   *
   * @param app the application
   */
  record VerifyApp(PackageRef app) implements DeviceCommand<PackageInfo> {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code app} is {@code null}
     */
    public VerifyApp {
      Objects.requireNonNull(app, "app");
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-verifyApp").addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<PackageInfo> parser() {
      return OutputParsers.VERIFY_APP;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /**
   * Downloads a file from an application's sandbox.
   *
   * @param app the application; a name-and-id reference must carry the name
   * @param path the file, relative to the sandbox
   * @param saveAs where to store it locally, or {@code null} to receive the content in the result
   *     (text files only; a binary file must be saved to a path)
   */
  record GetFile(PackageRef app, String path, Path saveAs) implements DeviceCommand<FileDownload> {

    /**
     * Creates the command.
     *
     * @throws InvalidArgumentException if the path is blank or the reference lacks the package name
     */
    public GetFile {
      app.requireName();
      if (path == null || path.isBlank()) {
        throw new InvalidArgumentException("path", "is required");
      }
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-getFile", path, saveAs == null ? "-" : saveAs.toString())
          .addAll(app.arguments())
          .build();
    }

    @Override
    public OutputParser<FileDownload> parser() {
      return OutputParsers.FILE_DOWNLOAD;
    }

    @Override
    public boolean readOnly() {
      return true;
    }
  }

  /**
   * Uploads a file into an application's sandbox.
   *
   * @param app the application, identified by name and id or by full name; a BAR file is not
   *     accepted
   * @param localPath the local file to upload
   * @param path the destination file, relative to the sandbox; not a directory
   */
  record PutFile(PackageRef app, Path localPath, String path) implements DeviceCommand<FileUpload> {

    /**
     * Creates the command.
     *
     * @throws InvalidArgumentException if the path is blank, the reference lacks the package name,
     *     or it is a BAR file
     */
    public PutFile {
      if (app instanceof PackageRef.BarFile) {
        throw new InvalidArgumentException(
            "bar", "is not accepted by this method; use packageName+packageId");
      }
      app.requireName();
      Objects.requireNonNull(localPath, "localPath");
      if (path == null || path.isBlank()) {
        throw new InvalidArgumentException("path", "is required");
      }
    }

    @Override
    public List<String> arguments() {
      return CommandLine.of("-putFile", localPath.toString(), path).addAll(app.arguments()).build();
    }

    @Override
    public OutputParser<FileUpload> parser() {
      return OutputParsers.FILE_UPLOAD;
    }
  }

  /**
   * Installs a debug token on the device.
   *
   * @param token the debug token file
   */
  record InstallDebugToken(Path token) implements DeviceCommand<NoData> {

    /**
     * Creates the command.
     *
     * @throws NullPointerException if {@code token} is {@code null}
     */
    public InstallDebugToken {
      Objects.requireNonNull(token, "token");
    }

    @Override
    public List<String> arguments() {
      return List.of("-installDebugToken", token.toString());
    }

    @Override
    public OutputParser<NoData> parser() {
      return OutputParsers.NO_DATA;
    }
  }

  /**
   * Lists the devices the deploy tool detects. Needs no login and no Development Mode: the output
   * was the same with Development Mode disabled. Devices found over Wi-Fi have been captured; how a
   * USB-connected device is listed has not.
   */
  record ListDevices() implements DeviceCommand<DetectedDevices> {
    @Override
    public List<String> arguments() {
      return List.of("-devices");
    }

    @Override
    public OutputParser<DetectedDevices> parser() {
      return OutputParsers.DETECTED_DEVICES;
    }

    @Override
    public boolean readOnly() {
      return true;
    }

    @Override
    public boolean requiresDevice() {
      return false;
    }

    /**
     * The tool prints the {@code Info:} lines through its output stream but the device lines with
     * {@code System.out}.
     */
    @Override
    public boolean printsToStandardOutput() {
      return true;
    }
  }
}
