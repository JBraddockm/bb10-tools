package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.session.DeviceCommand;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Replays recorded device answers through the real conversation of one command and prints the
 * events the daemon would have sent. No device is needed, so parsers can be developed and checked
 * against fixtures.
 *
 * <p>Standard input holds one recorded response body per request the command sends. When a command
 * sends several requests, the bodies are separated by a line that contains only {@code ---}.
 */
public final class FixtureReplay {

  private FixtureReplay() {}

  /**
   * Replays recorded device answers from standard input through the named command, writing the
   * result to standard output.
   *
   * @param methodName the wire name of the command, with an optional dot-separated argument
   * @return an exit code: 0 for success, 2 for a usage error
   */
  public static int run(String methodName) {
    return run(
        methodName,
        System.in,
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8),
        System.err);
  }

  /**
   * Replays recorded device answers from {@code input} through the named command, writing the
   * result to {@code output}. Unlike {@link #run(String)}, this overload does not read {@link
   * System#in} or write to {@link FileDescriptor#out}, so it can be called in a test.
   *
   * @param methodName the wire name of the command, with an optional dot-separated argument
   * @param input where the recording is read from
   * @param output where the result is written
   * @param error where usage errors are written
   * @return an exit code: 0 for success, 2 for a usage error
   */
  public static int run(
      String methodName, InputStream input, PrintStream output, PrintStream error) {
    var parts = methodName.split("\\.", 2);
    var method = LinkMethod.byWireName(parts[0]);
    Optional<DeviceCommand<?>> command;
    try {
      var argument = parts.length == 2 ? Optional.of(parts[1]) : Optional.<String>empty();
      command = method.flatMap(found -> found.replayCommand(argument.orElse(null)));
    } catch (InvalidArgumentException failure) {
      error.println(failure.getMessage());
      return 2;
    }
    if (command.isEmpty()) {
      error.println("unknown or not replayable command: " + methodName);
      return 2;
    }
    String recording;
    try {
      recording = new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    replay(
        command.get(),
        method.orElseThrow().wireName(),
        split(recording),
        Reply.standalone(output, 0));
    return 0;
  }

  /**
   * Splits a recording into the answers it holds. A recording without separator is one answer, byte
   * for byte; the separator line itself belongs to no answer.
   */
  static List<String> split(String input) {
    return List.of(input.split("(?m)^---[ \\t]*\\r?(?:\\n|\\z)", -1));
  }

  private static <R extends CommandResult> void replay(
      DeviceCommand<R> command, String methodName, List<String> documents, Reply reply) {
    reply.complete(methodName, DeviceSession.replay(command, documents, reply), false);
  }
}
