package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.session.DeviceCommand;
import com.jbraddockm.link.session.DeviceSession;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
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
   * Reads recorded answers from standard input and prints the resulting events to standard output.
   *
   * @param methodName the wire name of the method that produced the answers, for example {@code
   *     getDeviceProperties}; a method that takes an argument is written with a dot, for example
   *     {@code getPimItems.contacts}
   * @return the process exit status: 0, or 2 if the method is unknown or cannot be replayed
   */
  public static int run(String methodName) {
    var parts = methodName.split("\\.", 2);
    var method = LinkMethod.byWireName(parts[0]);
    Optional<DeviceCommand<?>> command;
    try {
      var argument = parts.length == 2 ? Optional.of(parts[1]) : Optional.<String>empty();
      command = method.flatMap(found -> found.replayCommand(argument));
    } catch (InvalidArgumentException failure) {
      System.err.println(failure.getMessage());
      return 2;
    }
    if (command.isEmpty()) {
      System.err.println("unknown or not replayable command: " + methodName);
      return 2;
    }
    String input;
    try {
      input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    var stdout =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    replay(
        command.get(), method.orElseThrow().wireName(), split(input), Reply.standalone(stdout, 0));
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
