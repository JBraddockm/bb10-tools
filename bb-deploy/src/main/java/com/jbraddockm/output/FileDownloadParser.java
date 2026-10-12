package com.jbraddockm.output;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Parses {@code -getFile}. Observed, with exit status 0, for a text file printed to the terminal:
 *
 * <pre>
 * Info: Sending request: GET_FILE
 * Info: Action: Get File
 * Info: Package com.example.Term49.gYABgFEqMSpja5S6kL.DWpWmq50
 * Info: Asset Path data/hello.txt
 * Info: Sending File: 20
 * hello from the host
 * </pre>
 *
 * <p>and for a file saved to a path, where the announcement is followed by {@code Info: File saved:
 * /tmp/hello-back.txt} and nothing else. There is no {@code result::} line in either case. The
 * number after {@code Sending File:} is the size of the file in bytes. A file that does not exist
 * fails with {@code Error: Cannot open file ...: No such file or directory} (exit status 2), raised
 * by the tool itself.
 */
final class FileDownloadParser implements OutputParser<FileDownload> {

  private static final String ANNOUNCEMENT = "Info: Sending File: ";

  @Override
  public FileDownload parse(List<String> lines) {
    var announcement =
        IntStream.range(0, lines.size())
            .filter(index -> lines.get(index).startsWith(ANNOUNCEMENT))
            .findFirst();
    var savedTo = OutputText.lastValueAfter(lines, "Info: File saved: ");
    // Everything after the announcement is the file, unless it was saved instead of printed.
    var content =
        announcement.isPresent() && savedTo.isEmpty()
            ? String.join("\n", lines.subList(announcement.getAsInt() + 1, lines.size()))
            : null;
    return new FileDownload(
        OutputText.lastValueAfter(lines, "Info: Package ").orElse(null),
        OutputText.lastValueAfter(lines, "Info: Asset Path ").orElse(null),
        announcement.isPresent()
            ? OutputText.parseLong(
                    lines.get(announcement.getAsInt()).substring(ANNOUNCEMENT.length()).strip())
                .orElse(null)
            : null,
        savedTo.orElse(null),
        content);
  }
}
