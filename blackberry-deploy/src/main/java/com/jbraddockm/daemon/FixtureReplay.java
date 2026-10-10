package com.jbraddockm.daemon;

import com.jbraddockm.device.DeviceSession;
import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.OutputParser;
import com.jbraddockm.output.OutputParsers;
import java.io.BufferedReader;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Replays recorded output of one command through the real interpretation code and prints the events
 * the daemon would have sent. No device is needed, so parsers can be developed and checked against
 * fixtures.
 */
public final class FixtureReplay {

  private FixtureReplay() {}

  /**
   * Reads recorded output from standard input and prints the resulting events to standard output.
   *
   * @param commandName the command that produced the output, for example {@code listInstalledApps}
   * @return the process exit status: 0, or 2 if the command is unknown
   */
  public static int run(String commandName) {
    var parser = OutputParsers.forCommand(commandName);
    if (parser.isEmpty()) {
      System.err.println("unknown command: " + commandName);
      return 2;
    }
    var lines =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))
            .lines()
            .toList();
    var stdout =
        new PrintStream(new FileOutputStream(FileDescriptor.out), false, StandardCharsets.UTF_8);
    replay(
        parser.get(),
        commandName,
        lines,
        Reply.to(new EventWriter(stdout), new RequestId.Numeric(0)));
    return 0;
  }

  private static <T extends CommandResult> void replay(
      OutputParser<T> parser, String commandName, List<String> lines, Reply reply) {
    reply.complete(commandName, DeviceSession.replay(parser, lines, reply));
  }
}
