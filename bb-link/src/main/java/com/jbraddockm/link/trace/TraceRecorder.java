package com.jbraddockm.link.trace;

import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.session.ExchangeObserver;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Writes every exchange with a device to its own numbered file.
 *
 * <p>Cookies, the login answer, the password of the device's file share and the values that
 * identify a device or its owner (PIN, serial numbers, IMEI, chip identifiers, BlackBerry ID,
 * package fingerprints) are replaced by {@code ***} before anything is written, so a trace can be
 * shared. Cookies appear by name only. A failure to write is reported on standard error and never
 * disturbs the command.
 */
public final class TraceRecorder implements ExchangeObserver {

  private final Path directory;
  private final AtomicInteger counter = new AtomicInteger();

  /**
   * Creates the recorder and its directory.
   *
   * @param directory where the files go; created if it does not exist
   * @throws IOException if the directory cannot be created
   */
  public TraceRecorder(Path directory) throws IOException {
    this.directory = Files.createDirectories(directory);
  }

  @Override
  public void onExchange(
      String requestLine, String requestBody, List<String> cookieNames, CgiResponse response) {
    var file = directory.resolve("%04d.txt".formatted(counter.incrementAndGet()));
    var content =
        ">>> "
            + requestLine
            + (cookieNames.isEmpty()
                ? ""
                : "\n>>> Cookie: "
                    + cookieNames.stream()
                        .map(name -> name + "=***")
                        .collect(Collectors.joining("; ")))
            + "\n"
            + requestBody
            + "\n\n<<< HTTP "
            + response.statusCode()
            + " "
            + (response.contentType() == null ? "(no content type)" : response.contentType())
            + (response.setCookie() == null ? "" : "\n<<< Set-Cookie: " + response.setCookie())
            + "\n"
            + response.body()
            + "\n";
    try {
      Files.writeString(file, Scrub.identifiers(content), StandardCharsets.UTF_8);
    } catch (IOException failure) {
      System.err.println("bb-link: could not write trace " + file + ": " + failure.getMessage());
    }
  }

  @Override
  public void onFailedExchange(
      String requestLine, String requestBody, List<String> cookieNames, IOException failure) {
    var file = directory.resolve("%04d.txt".formatted(counter.incrementAndGet()));
    var content =
        ">>> "
            + requestLine
            + (cookieNames.isEmpty()
                ? ""
                : "\n>>> Cookie: "
                    + cookieNames.stream()
                        .map(name -> name + "=***")
                        .collect(Collectors.joining("; ")))
            + "\n"
            + requestBody
            + "\n\n<<< "
            + failure.getClass().getSimpleName()
            + ": "
            + (failure.getMessage() == null ? "(no message)" : failure.getMessage())
            + "\n";
    try {
      Files.writeString(file, Scrub.identifiers(content), StandardCharsets.UTF_8);
    } catch (IOException writeFailure) {
      System.err.println(
          "bb-link: could not write trace " + file + ": " + writeFailure.getMessage());
    }
  }
}
