package com.jbraddockm.core.daemon;

import static com.jbraddockm.core.daemon.ServerRun.errorCode;
import static com.jbraddockm.core.daemon.ServerRun.errorMessage;
import static com.jbraddockm.core.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.core.daemon.StubService.StubSession;
import com.jbraddockm.core.testing.Resources;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link DaemonServer} over a {@link StubService}: greeting, rejected and refused
 * requests, session lifecycle, command ordering and concurrency, shutdown and draining. Gates in
 * the stub make ordering deterministic.
 */
@DisplayName("DaemonServer: the loop of the protocol")
class DaemonServerTest {

  private static final Duration SHORT = Duration.ofMillis(250);

  private StubService service;
  private ServerRun<StubSession> run;

  @BeforeEach
  void startTheServer() throws Exception {
    service = new StubService();
    run = new ServerRun<>(service, Duration.ofSeconds(5));
    run.hello();
  }

  @AfterEach
  void stopTheServer() throws Exception {
    service.blockGate.countDown();
    service.holdGate.countDown();
    service.establishGate.countDown();
    run.close();
  }

  private void connect(String id, String host) {
    run.send(id, "request--connect.json", Map.of("host", host));
  }

  private void command(String id, String method, String session) {
    run.send(id, "request--session-command.json", Map.of("method", method, "session", session));
  }

  private void ping(String id) {
    run.send(id, "request--standalone.json", Map.of("method", "ping"));
  }

  private String established(String id, String host) throws Exception {
    connect(id, host);
    assertThat(run.done(Long.valueOf(id)).get("ok")).isEqualTo(true);
    return String.valueOf(
        object(run.eventOf(Long.valueOf(id), "result").get("data")).get("session"));
  }

  @Nested
  @DisplayName("what it says when it starts")
  class Starting {

    @Test
    @DisplayName(
        "is a greeting with the type, the version of the protocol, the tool, its version, and the"
            + " methods: the three of the protocol, then those of the service")
    void greets() throws Exception {
      var hello = run.hello();

      assertThat(List.copyOf(hello.keySet()))
          .containsExactly("type", "protocol", "tool", "version", "methods");
      assertThat(hello)
          .containsEntry("type", "hello")
          .containsEntry("protocol", 1L)
          .containsEntry("tool", "stub")
          .containsEntry("version", "9.9");
      assertThat(hello.get("methods"))
          .isEqualTo(
              Resources.text(DaemonServerTest.class, "server--methods.txt").lines().toList());
      assertThat(DaemonServer.PROTOCOL_VERSION).isEqualTo(1);
    }

    @Test
    @DisplayName("takes no notice of a blank line: it is not a request and gets no answer")
    void aBlankLine() throws Exception {
      run.write(ServerRun.line("lines--blank.txt"));
      ping("1");

      run.done(1L);

      assertThat(run.events())
          .hasSize(4)
          .allSatisfy(event -> assertThat(event.get("id")).isIn(null, 1L));
      assertThat(run.events().get(1).get("id")).isEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("what it cannot read")
  class Rejecting {

    @ParameterizedTest(name = "{0}")
    @CsvSource(
        value = {
          "lines--not-json.txt, ",
          "lines--without-id.json, ",
          "lines--without-method.json, 1"
        })
    @DisplayName(
        "a line that is not a request is answered with INVALID_REQUEST, carrying the identifier"
            + " when there is one, and null when there is not")
    void aLineThatIsNotARequest(String resource, Long id) throws Exception {
      run.write(ServerRun.line(resource));

      var done = run.done(id);

      assertThat(errorCode(done)).isEqualTo("INVALID_REQUEST");
      assertThat(done).containsKey("id").containsEntry("ok", false);
      assertThat(done.get("id")).isEqualTo(id);
    }
  }

  @Nested
  @DisplayName("what it refuses to run")
  class Refusing {

    @Test
    @DisplayName("a method that the service does not have is UNKNOWN_METHOD, naming it")
    void anUnknownMethod() throws Exception {
      run.send("2", "request--standalone.json", Map.of("method", "nope"));

      var done = run.done(2L);

      assertThat(errorCode(done)).isEqualTo("UNKNOWN_METHOD");
      assertThat(errorMessage(done)).isEqualTo("unknown method 'nope'");
    }

    @Test
    @DisplayName("a session that was never made is UNKNOWN_SESSION, naming it")
    void anUnknownSession() throws Exception {
      run.send("3", "request--echo.json", Map.of("session", "s9", "text", "x"));

      var done = run.done(3L);

      assertThat(errorCode(done)).isEqualTo("UNKNOWN_SESSION");
      assertThat(errorMessage(done)).isEqualTo("no such session: s9");
    }

    @Test
    @DisplayName(
        "an argument that is wrong is INVALID_ARGUMENT, and is found before the session is: a"
            + " request with no text to a session that does not exist is about the text")
    void anInvalidArgumentComesFirst() throws Exception {
      run.send("4", "request--echo-without-text.json", Map.of("session", "s9"));

      var done = run.done(4L);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(object(object(done.get("error")).get("details"))).containsEntry("param", "text");
    }

    @Test
    @DisplayName(
        "a command that rejects its argument with a plain IllegalArgumentException is"
            + " INVALID_ARGUMENT, with its words")
    void aPlainIllegalArgument() throws Exception {
      var session = established("1", "ok");

      command("2", "illegal", session);

      var done = run.done(2L);
      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorMessage(done)).isEqualTo("nope");
    }
  }

  @Nested
  @DisplayName("commands that need no session")
  class Standalone {

    @Test
    @DisplayName(
        "run without one, send their own events and their result, end once, and echo the identifier"
            + " as a text when it was one")
    void aStandaloneCommand() throws Exception {
      ping("\"p\"");

      run.done("p");

      assertThat(run.typesOf("p")).containsExactly("pong", "result", "done");
      assertThat(object(run.eventOf("p", "pong"))).containsEntry("n", 1L);
    }

    @Test
    @DisplayName("run while a command of a session is held: they do not wait for it")
    void doNotWaitForASession() throws Exception {
      var session = established("1", "ok");
      command("20", "block", session);
      assertThat(service.blockStarted.await(10, TimeUnit.SECONDS)).isTrue();

      ping("22");

      assertThat(run.done(22L).get("ok")).isEqualTo(true);
      assertThat(run.doneWithin(20L, Duration.ofMillis(50))).isFalse();
    }
  }

  @Nested
  @DisplayName("connect")
  class Connecting {

    @Test
    @DisplayName(
        "that is established gives the session first and then what the service said of it, and ends"
            + " with ok")
    void anEstablishedSession() throws Exception {
      connect("1", "ok");

      var done = run.done(1L);

      assertThat(done.get("ok")).isEqualTo(true);
      var data = object(run.eventOf(1L, "result").get("data"));
      assertThat(List.copyOf(data.keySet())).containsExactly("session", "host");
      assertThat(data).containsEntry("session", "s1").containsEntry("host", "ok");
      assertThat(run.typesOf(1L)).containsExactly("result", "done");
    }

    @Test
    @DisplayName(
        "that is rejected by the service ends with its error, leaves no session behind, and closes"
            + " the one that it opened")
    void aRejectedConnect() throws Exception {
      connect("10", "bad");

      var done = run.done(10L);
      run.send("11", "request--echo.json", Map.of("session", "s1", "text", "x"));

      assertThat(errorCode(done)).isEqualTo("CONNECTION_FAILED");
      assertThat(errorMessage(done)).isEqualTo("no route");
      assertThat(run.typesOf(10L)).containsExactly("done");
      assertThat(service.opened).hasSize(1);
      assertThat(service.opened.getFirst().closed).isTrue();
      assertThat(errorCode(run.done(11L))).isEqualTo("UNKNOWN_SESSION");
    }

    @Test
    @DisplayName("that makes the service throw ends with INTERNAL, and closes the session as well")
    void aConnectThatThrows() throws Exception {
      connect("10", "explode");

      var done = run.done(10L);

      assertThat(errorCode(done)).isEqualTo("INTERNAL");
      assertThat(errorMessage(done)).isEqualTo("explosion");
      assertThat(service.opened).hasSize(1);
      assertThat(service.opened.getFirst().closed).isTrue();
    }

    @Test
    @DisplayName(
        "that is refused by the service before a session is made is INVALID_ARGUMENT, and opens"
            + " nothing")
    void anArgumentThatIsWrong() throws Exception {
      run.send("10", "request--connect-without-host.json");

      var done = run.done(10L);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(service.opened).isEmpty();
    }

    @Test
    @DisplayName(
        "that is not finished says so to a command that uses its session: the session is not usable"
            + " before the reply to the connect, and the words say that it is still connecting")
    void aSessionThatIsStillConnecting() throws Exception {
      connect("12", "slow");
      run.send("13", "request--echo.json", Map.of("session", "s1", "text", "x"));

      var early = run.done(13L);

      assertThat(errorCode(early)).isEqualTo("UNKNOWN_SESSION");
      assertThat(errorMessage(early))
          .isEqualTo(
              "session 's1' is still connecting: wait for the connect reply before using it");
      assertThat(run.doneWithin(12L, Duration.ofMillis(50))).isFalse();

      service.establishGate.countDown();
      assertThat(run.done(12L).get("ok")).isEqualTo(true);
    }

    @Test
    @DisplayName(
        "gives each session a name of its own, in the order of the connects, a rejected one"
            + " included")
    void sessionsAreNumbered() throws Exception {
      connect("1", "bad");
      run.done(1L);

      assertThat(established("2", "ok")).isEqualTo("s2");
      assertThat(established("3", "ok")).isEqualTo("s3");
    }
  }

  @Nested
  @DisplayName("commands on a session")
  class OnASession {

    @Test
    @DisplayName("send their log, their progress and their result, in this order, and end once")
    void theEventsOfACommand() throws Exception {
      var session = established("1", "ok");

      run.send("14", "request--echo.json", Map.of("session", session, "text", "hi"));
      run.done(14L);

      assertThat(run.typesOf(14L)).containsExactly("log", "progress", "result", "done");
      assertThat(object(run.eventOf(14L, "result").get("data")))
          .containsEntry("text", "hi")
          .containsEntry("host", "ok");
    }

    @Test
    @DisplayName("that throw end with INTERNAL, with the words of the exception, and end once")
    void aCommandThatThrows() throws Exception {
      var session = established("1", "ok");

      command("15", "boom", session);

      var done = run.done(15L);
      assertThat(errorCode(done)).isEqualTo("INTERNAL");
      assertThat(errorMessage(done)).isEqualTo("kaboom");
      assertThat(run.typesOf(15L).stream().filter("done"::equals)).hasSize(1);
    }

    @Test
    @DisplayName(
        "that forget to end are ended for them with INTERNAL, once, so that a client is never left"
            + " waiting")
    void aCommandThatForgetsToEnd() throws Exception {
      var session = established("1", "ok");

      command("16", "silent", session);

      var done = run.done(16L);
      assertThat(errorCode(done)).isEqualTo("INTERNAL");
      assertThat(errorMessage(done)).isEqualTo("the command ended without a final event");
      assertThat(run.typesOf(16L)).containsExactly("log", "done");
    }

    @Test
    @DisplayName(
        "run one after the other on one session: the second does not begin before the first has"
            + " ended, though it has been accepted")
    void oneAfterTheOther() throws Exception {
      var session = established("1", "ok");
      command("20", "block", session);
      assertThat(service.blockStarted.await(10, TimeUnit.SECONDS)).isTrue();
      run.send("21", "request--echo.json", Map.of("session", session, "text", "later"));
      ping("22");
      run.done(22L);

      assertThat(run.doneWithin(21L, SHORT)).as("the second command waits for the first").isFalse();
      assertThat(run.typesOf(21L)).isEmpty();

      service.blockGate.countDown();
      run.done(21L);
      assertThat(service.order).containsExactly("block starts", "block ends", "echo later");
    }

    @Test
    @DisplayName(
        "run at the same time on two sessions: a command of one is held, and one of the other is"
            + " answered")
    void sessionsAreIndependent() throws Exception {
      var first = established("1", "ok");
      var second = established("2", "ok");
      command("20", "block", first);
      assertThat(service.blockStarted.await(10, TimeUnit.SECONDS)).isTrue();

      run.send("21", "request--echo.json", Map.of("session", second, "text", "free"));

      assertThat(run.done(21L).get("ok")).isEqualTo(true);
      assertThat(service.order).containsExactly("block starts", "echo free");
      service.blockGate.countDown();
      run.done(20L);
    }
  }

  @Nested
  @DisplayName("disconnect")
  class Disconnecting {

    @Test
    @DisplayName(
        "closes the session, and a second disconnect, or one of a session that never was, is fine:"
            + " it is idempotent")
    void isIdempotent() throws Exception {
      var session = established("1", "ok");

      run.send("30", "request--disconnect.json", Map.of("session", session));
      run.done(30L);
      run.send("31", "request--disconnect.json", Map.of("session", session));
      run.send("32", "request--disconnect.json", Map.of("session", "s77"));

      assertThat(service.opened).hasSize(1);
      assertThat(service.opened.getFirst().closed).isTrue();
      assertThat(run.done(31L).get("ok")).isEqualTo(true);
      assertThat(run.done(32L).get("ok")).isEqualTo(true);
    }

    @Test
    @DisplayName("needs the session, as INVALID_ARGUMENT naming it")
    void needsTheSession() throws Exception {
      run.send("33", "request--disconnect-without-session.json");

      var done = run.done(33L);

      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(object(object(done.get("error")).get("details")))
          .containsEntry("param", "session");
    }

    @Test
    @DisplayName(
        "waits for what the session still has to do, and closes it after: the end of the disconnect"
            + " comes after the end of the command that was running")
    void waitsForPendingWork() throws Exception {
      var session = established("1", "ok");
      command("20", "block", session);
      assertThat(service.blockStarted.await(10, TimeUnit.SECONDS)).isTrue();
      run.send("30", "request--disconnect.json", Map.of("session", session));
      ping("31");
      run.done(31L);

      assertThat(run.doneWithin(30L, SHORT)).as("the disconnect waits").isFalse();
      assertThat(service.opened.getFirst().closed).isFalse();

      service.blockGate.countDown();
      run.done(30L);
      var types =
          run.events().stream()
              .filter(event -> "done".equals(event.get("type")))
              .map(event -> event.get("id"))
              .toList();
      assertThat(types.indexOf(20L)).isLessThan(types.indexOf(30L));
      assertThat(service.opened.getFirst().closed).isTrue();
    }
  }

  @Nested
  @DisplayName("shutdown")
  class ShuttingDown {

    @Test
    @DisplayName(
        "is answered, and then the server stops reading: a request that comes right after it is"
            + " never handled")
    void stopsReading() throws Exception {
      run.write(
          Resources.text(DaemonServerTest.class, "lines--shutdown-then-ping.txt")
              .lines()
              .toArray(String[]::new));

      assertThat(run.done(1L).get("ok")).isEqualTo(true);

      assertThat(run.stoppedWithin(Duration.ofSeconds(10))).isTrue();
      assertThat(run.typesOf(2L)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the end of the input")
  class EndOfInput {

    @Test
    @DisplayName("with nothing in flight stops the server at once")
    void nothingInFlight() throws Exception {
      run.endInput();

      assertThat(run.stoppedWithin(Duration.ofSeconds(10))).isTrue();
      assertThat(run.failure()).isNull();
    }

    @Test
    @DisplayName(
        "with a command still running does not stop the server: it waits for what it accepted,"
            + " answers it, and then stops")
    void drainsWhatWasAccepted() throws Exception {
      var session = established("40", "ok");
      command("41", "hold", session);
      assertThat(service.holdStarted.await(10, TimeUnit.SECONDS)).isTrue();

      run.endInput();

      assertThat(run.stoppedWithin(SHORT)).as("the server waits for the request").isFalse();
      service.holdGate.countDown();
      assertThat(run.stoppedWithin(Duration.ofSeconds(10))).isTrue();
      assertThat(run.done(41L).get("ok")).isEqualTo(true);
    }

    @Test
    @DisplayName(
        "gives up on a command that does not end within the time to drain, says so on the error"
            + " output, and interrupts the command that is still running")
    void givesUpAfterTheDrainTime() throws Exception {
      var captured = new ByteArrayOutputStream();
      var original = System.err;
      System.setErr(new PrintStream(captured, true, StandardCharsets.UTF_8));
      try (var impatient = new ServerRun<>(service, Duration.ofMillis(100))) {
        impatient.hello();
        impatient.send("1", "request--connect.json", Map.of("host", "ok"));
        impatient.done(1L);
        impatient.send(
            "2", "request--session-command.json", Map.of("method", "hold", "session", "s1"));
        assertThat(service.holdStarted.await(10, TimeUnit.SECONDS)).isTrue();

        impatient.endInput();

        assertThat(impatient.stoppedWithin(Duration.ofSeconds(10))).isTrue();
        impatient.done(2L);
        assertThat(service.order)
            .as("the held command was interrupted and not released")
            .containsExactly("hold interrupted");
      } finally {
        System.setErr(original);
      }

      assertThat(captured.toString(StandardCharsets.UTF_8))
          .contains(Resources.text(DaemonServerTest.class, "server--drain-warning.txt").strip());
    }
  }

  @Nested
  @DisplayName("a service that is wrong")
  class WrongService {

    private void assertRefused(StubService wrong, String message) {
      assertThatIllegalArgumentException()
          .isThrownBy(
              () ->
                  DaemonServer.serve(
                      wrong,
                      InputStream.nullInputStream(),
                      OutputStream.nullOutputStream(),
                      Duration.ofSeconds(1)))
          .withMessage(message);
    }

    @Test
    @DisplayName(
        "that declares a method of the protocol, or the same method twice, is refused before"
            + " anything is written")
    void reservedAndDuplicatedMethods() {
      var connect =
          new CommandSpec.SessionScoped<StubSession>("connect", _ -> (_, reply) -> reply.succeed());
      var echoAgain =
          new CommandSpec.SessionScoped<StubSession>("echo", _ -> (_, reply) -> reply.succeed());

      assertRefused(
          new StubService(List.of(connect)), "method declared twice or reserved: connect");
      assertRefused(new StubService(List.of(echoAgain)), "method declared twice or reserved: echo");
    }
  }
}
