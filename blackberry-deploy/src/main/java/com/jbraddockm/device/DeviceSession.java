package com.jbraddockm.device;

import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.LogLevel;
import com.jbraddockm.output.OutputParser;
import com.qnx.bbt.deploy.Deploy;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

/**
 * One login to one device, reused by every command run through it.
 *
 * <p>The first command logs in; later commands reuse the HTTP client and the login cookie, which
 * avoids the expensive challenge-response login. The session recovers on its own in the two cases
 * that are safe:
 *
 * <ul>
 *   <li>If the device has forgotten the login, the session logs in again and repeats the command
 *       once. This applies to every command, because the device rejected it before running it.
 *   <li>If a pooled connection died, a {@linkplain DeviceCommand#readOnly() read-only} command is
 *       repeated once. Commands that change something are never repeated automatically.
 * </ul>
 *
 * <p>A wrong password is never retried, because every attempt counts against the device's limit.
 *
 * <p>Commands run one at a time: the underlying tool keeps per-command state, so concurrent calls
 * are serialised. A caller that needs ordering across threads must supply it.
 */
public final class DeviceSession implements AutoCloseable {

  private final DeviceTarget target;
  private final Deploy deploy = new Deploy();
  private final ReentrantLock commandLock = new ReentrantLock();

  private DeviceSession(DeviceTarget target) {
    this.target = target;
    deploy.setUseMemoryCookieStore(true); // never write the login cookie to disk
  }

  /**
   * Creates a session for a device. Nothing is sent until the first command runs.
   *
   * @param target the device and its password
   * @return the session
   */
  public static DeviceSession forTarget(DeviceTarget target) {
    return new DeviceSession(Objects.requireNonNull(target, "target"));
  }

  /**
   * Creates a session for commands that need no device, such as {@link DeviceCommand.ListDevices}.
   *
   * @return the session
   */
  public static DeviceSession withoutDevice() {
    return new DeviceSession(null);
  }

  /**
   * Interprets previously captured output exactly as {@link #execute} would interpret live output,
   * including the failures the real tool would have raised. Needs no device, so it is the basis for
   * testing parsers against recorded output.
   *
   * @param parser the parser of the command that produced the output
   * @param capturedLines the recorded lines, without line terminators
   * @param listener receives the log and progress reports the lines contain
   * @param <T> the type of result the command produces
   * @return how the recorded run ended
   */
  public static <T extends CommandResult> CommandOutcome<T> replay(
      OutputParser<T> parser, List<String> capturedLines, CommandListener listener) {
    capturedLines.forEach(line -> RunInterpreter.forward(line, parser, listener));
    return RunInterpreter.toolErrorLine(capturedLines)
        .map(
            errorLine ->
                RunInterpreter.failed(
                    parser,
                    capturedLines,
                    FailureClassifier.classify(new Deploy.DeployException(errorLine)),
                    listener))
        .orElseGet(() -> RunInterpreter.completed(parser, capturedLines, listener));
  }

  /**
   * Runs a command and waits for it to end.
   *
   * @param command the command
   * @param listener receives log and progress reports while the command runs
   * @param <T> the type of result the command produces
   * @return how the command ended; never throws for a failure of the command itself
   * @throws IllegalStateException if the command needs a device and the session has none
   */
  public <T extends CommandResult> CommandOutcome<T> execute(
      DeviceCommand<T> command, CommandListener listener) {
    Objects.requireNonNull(command, "command");
    Objects.requireNonNull(listener, "listener");
    commandLock.lock();
    try {
      return executeLocked(command, listener);
    } finally {
      commandLock.unlock();
    }
  }

  /**
   * Discards the login and closes the connections.
   *
   * <p>If a command is running, this waits for it to end, because the underlying tool synchronises
   * both.
   */
  @Override
  public void close() {
    deploy.shutdownHttpClient();
  }

  private <T extends CommandResult> CommandOutcome<T> executeLocked(
      DeviceCommand<T> command, CommandListener listener) {
    var parser = command.parser();
    var arguments = commandLine(command);
    var capturedLines = new ArrayList<String>();
    var loginRenewed = false;
    var connectionRenewed = false;

    while (true) {
      capturedLines.clear(); // a repeated attempt starts from a clean slate

      // The tool may print from threads of its own, so lines from either stream are taken one at a
      // time, in the order they complete.
      java.util.function.Consumer<String> lineHandler =
          line -> {
            synchronized (capturedLines) {
              capturedLines.add(line);
              RunInterpreter.forward(line, parser, listener);
            }
          };

      try (var collector = new LineCollector(lineHandler);
          var stdoutCollector = new LineCollector(lineHandler)) {

        deploy.setOut(collector.stream());

        try {
          try (var _ =
              command.printsToStandardOutput()
                  ? StandardOutputCapture.capture(stdoutCollector)
                  : (StandardOutputCapture.Capture) () -> {}) {
            deploy.runOnSession(arguments);
          }
          collector.finish();
          stdoutCollector.finish();

          return RunInterpreter.completed(parser, snapshot(capturedLines), listener);

        } catch (Exception failure) {
          collector.finish();
          stdoutCollector.finish();

          if (!FailureClassifier.leavesSessionUsable(failure)) {
            deploy.shutdownHttpClient();
          }

          if (FailureClassifier.isStaleAuthentication(failure) && !loginRenewed) {
            loginRenewed = true;
            listener.onLog(LogLevel.DEBUG, "session re-established");
            continue;
          }

          if (command.readOnly()
              && !connectionRenewed
              && FailureClassifier.isStaleConnection(failure)) {
            connectionRenewed = true;
            listener.onLog(LogLevel.DEBUG, "session re-established");
            continue;
          }

          return RunInterpreter.failed(
              parser, snapshot(capturedLines), FailureClassifier.classify(failure), listener);
        }
      }
    }
  }

  /** A copy taken under the lock, in case the tool is still printing from another thread. */
  private static List<String> snapshot(List<String> lines) {
    synchronized (lines) {
      return List.copyOf(lines);
    }
  }

  /**
   * Every call carries the device and the password: the tool forgets its parsed arguments between
   * calls and only logs in when it has no client. {@code -device} is explicit so that no argument
   * is mistaken for the address.
   */
  private String[] commandLine(DeviceCommand<?> command) {
    var line = CommandLine.of().addAll(command.arguments());
    if (command.requiresDevice()) {
      if (target == null) {
        throw new IllegalStateException("this session has no device, but the command needs one");
      }
      line.add("-device", target.commandLineAddress(), "-password", target.password());
    }
    return line.build().toArray(String[]::new);
  }
}
