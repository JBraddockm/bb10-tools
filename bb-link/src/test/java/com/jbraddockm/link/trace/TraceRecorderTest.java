package com.jbraddockm.link.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.apache.http.NoHttpResponseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link TraceRecorder}: file layout and numbering, cookie handling and redaction. */
@DisplayName("TraceRecorder: the exchanges with a device, kept in files")
class TraceRecorderTest {

  @TempDir private Path folder;

  private static String text(String resource) {
    return Resources.text(TraceRecorderTest.class, resource);
  }

  private static String piece(String resource) {
    return text(resource).strip();
  }

  private String file(int number) throws Exception {
    return Files.readString(folder.resolve("%04d.txt".formatted(number)));
  }

  @Test
  @DisplayName(
      "says on the error output that a file could not be written, and goes on: a trace must never"
          + " end an exchange")
  void aFileThatCannotBeWritten() throws Exception {
    var recorder = new TraceRecorder(folder);
    Files.delete(folder);
    Files.writeString(folder, "this is now a file, not a folder");
    var captured = new ByteArrayOutputStream();
    var original = System.err;
    System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
    try {
      recorder.onExchange("GET /x", "", List.of(), new CgiResponse(200, null, "", null));
    } finally {
      System.setErr(original);
    }

    assertThat(captured.toString(StandardCharsets.UTF_8))
        .startsWith("bb-link: could not write trace " + folder.resolve("0001.txt") + ": ");
  }

  @Test
  @DisplayName(
      "writes a numbered file for a failed exchange: the request line and the exception class and"
          + " message, but no response body")
  void aFailedExchangeIsRecorded() throws Exception {
    var recorder = new TraceRecorder(folder);
    recorder.onFailedExchange(
        "POST /cgi-bin/wipe.cgi",
        "wipe=wipe",
        List.of(),
        new NoHttpResponseException("The target server failed to respond"));

    var file = folder.resolve("0001.txt");
    assertThat(file).exists();
    var content = Files.readString(file, StandardCharsets.UTF_8);
    assertThat(content).startsWith(">>> POST /cgi-bin/wipe.cgi");
    assertThat(content)
        .contains("<<< NoHttpResponseException: The target server failed to respond");
    assertThat(content).doesNotContain("HTTP");
  }

  @Nested
  @DisplayName("what a file holds")
  class Holding {

    @Test
    @DisplayName("the request, its body, the status of the answer, its content type and its body")
    void aPlainExchange() throws Exception {
      var recorder = new TraceRecorder(folder);

      recorder.onExchange(
          "POST /cgi-bin/dynamicProperties.cgi",
          piece("trace--request-body.txt"),
          List.of(),
          new CgiResponse(200, "text/xml", piece("trace--response-body.xml"), null));

      assertThat(file(1)).isEqualTo(text("trace--exchange--plain.txt"));
    }

    @Test
    @DisplayName(
        "the names of the cookies that were sent, each with its value hidden by the recorder itself"
            + " (a name that the scrubbing does not know is hidden too), and never the values")
    void cookiesAreSentByName() throws Exception {
      var recorder = new TraceRecorder(folder);

      recorder.onExchange(
          "GET /cgi-bin/pimsync.cgi?command=list_accounts",
          "",
          List.of("dtmauth", "custom"),
          new CgiResponse(200, "text/xml", piece("trace--response-body.xml"), null));

      assertThat(file(1)).isEqualTo(text("trace--exchange--cookies.txt"));
    }

    @Test
    @DisplayName("an answer with no content type says so")
    void noContentType() throws Exception {
      var recorder = new TraceRecorder(folder);

      recorder.onExchange("GET /cgi-bin/x", "", List.of(), new CgiResponse(204, null, "", null));

      assertThat(file(1)).isEqualTo(text("trace--exchange--no-content-type.txt"));
    }

    @Test
    @DisplayName(
        "the cookie that the device sets, with its value hidden by the scrubbing that every trace"
            + " goes through")
    void aCookieThatIsSet() throws Exception {
      var recorder = new TraceRecorder(folder);

      recorder.onExchange(
          "GET /cgi-bin/login.cgi?request_version=1",
          "",
          List.of(),
          new CgiResponse(
              200, "text/xml", piece("trace--response-body.xml"), "dtmauth=TOKEN123; path=/"));

      assertThat(file(1))
          .isEqualTo(text("trace--exchange--set-cookie.txt"))
          .doesNotContain("TOKEN123");
    }

    @Test
    @DisplayName("the PIN of a device is hidden in what the device answers")
    void identifiersAreHidden() throws Exception {
      var recorder = new TraceRecorder(folder);

      recorder.onExchange(
          "POST /cgi-bin/wipe.cgi",
          "wipe=getpin",
          List.of(),
          new CgiResponse(200, "text/xml", piece("trace--response-with-identifiers.xml"), null));

      assertThat(file(1))
          .isEqualTo(text("trace--exchange--identifiers.txt"))
          .doesNotContain("2A3B4C5D");
    }
  }

  @Nested
  @DisplayName("the files")
  class Filing {

    @Test
    @DisplayName("are numbered in four digits, in the order of the exchanges")
    void areNumbered() throws Exception {
      var recorder = new TraceRecorder(folder);

      for (var index = 1; index <= 3; index++) {
        recorder.onExchange(
            "GET /cgi-bin/" + index, "", List.of(), new CgiResponse(200, "text/xml", "", null));
      }

      assertThat(folder)
          .isDirectoryContaining(path -> path.getFileName().toString().equals("0001.txt"));
      assertThat(file(1)).contains("/cgi-bin/1");
      assertThat(file(2)).contains("/cgi-bin/2");
      assertThat(file(3)).contains("/cgi-bin/3");
      assertThat(Files.list(folder).count()).isEqualTo(3);
    }

    @Test
    @DisplayName(
        "are made in a folder that is created, with the folders above it, when it is not there")
    void theFolderIsCreated() throws Exception {
      var nested = folder.resolve("a").resolve("b");

      new TraceRecorder(nested)
          .onExchange("GET /x", "", List.of(), new CgiResponse(200, null, "", null));

      assertThat(nested.resolve("0001.txt")).exists();
    }

    @Test
    @DisplayName(
        "go on being made in a folder that is there already, and are numbered from 1 by each"
            + " recorder")
    void aFolderThatIsThere() throws Exception {
      new TraceRecorder(folder)
          .onExchange("GET /first", "", List.of(), new CgiResponse(200, null, "", null));
      new TraceRecorder(folder)
          .onExchange("GET /second", "", List.of(), new CgiResponse(200, null, "", null));

      assertThat(file(1)).contains("GET /second");
    }

    @Test
    @DisplayName(
        "are all kept when the exchanges come from many threads at once, each in a file of its own")
    void manyThreadsAtOnce() throws Exception {
      var recorder = new TraceRecorder(folder);
      try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
        IntStream.range(0, 40)
            .forEach(
                index ->
                    pool.submit(
                        () ->
                            recorder.onExchange(
                                "GET /cgi-bin/" + index,
                                "",
                                List.of(),
                                new CgiResponse(200, null, "", null))));
        pool.shutdown();
        assertThat(pool.awaitTermination(20, TimeUnit.SECONDS)).isTrue();
      }

      assertThat(Files.list(folder).count()).isEqualTo(40);
      assertThat(
              IntStream.rangeClosed(1, 40)
                  .mapToObj(number -> "%04d.txt".formatted(number))
                  .map(folder::resolve))
          .allMatch(Files::exists);
    }
  }
}
