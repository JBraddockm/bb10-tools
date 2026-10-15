package com.jbraddockm.link.daemon;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.daemon.Reply;
import com.jbraddockm.link.session.DeviceCommand;
import com.jbraddockm.link.session.DeviceSession;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Replays recorded device answers through the real conversation of one command and prints the
 * events the daemon would have sent. No device is needed, so parsers can be developed and checked
 * against fixtures.
 *
 * <p>Standard input holds one recorded response body per request the command sends. When a command
 * sends several requests, the bodies are separated by a line that contains only {@code ---}.
 */
public final class FixtureReplay {

  private static final String SEPARATOR = "---";

  private FixtureReplay() {}

  /**
   * Reads recorded answers from standard input and prints the resulting events to standard output.
   *
   * @param methodName the wire name of the method that produced the answers, for example {@code
   *     getDeviceProperties}
   * @return the process exit status: 0, or 2 if the method is unknown or cannot be replayed
   */
  public static int run(String methodName) {
    var command = LinkMethod.byWireName(methodName).flatMap(LinkMethod::replayCommand);
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
    replay(command.get(), methodName, split(input), Reply.standalone(stdout, 0));
    return 0;
  }

  static List<String> split(String input) {
    var documents = new ArrayList<String>();
    var current = new StringBuilder();
    for (var line : input.split("\n", -1)) {
      if (line.strip().equals(SEPARATOR)) {
        documents.add(current.toString());
        current.setLength(0);
      } else {
        current.append(line).append('\n');
      }
    }
    documents.add(current.toString());
    return documents;
  }

  private static <R extends CommandResult> void replay(
      DeviceCommand<R> command, String methodName, List<String> documents, Reply reply) {
    reply.complete(methodName, DeviceSession.replay(command, documents, reply), false);
  }
}
