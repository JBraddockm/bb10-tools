package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.StepEffect;
import com.jbraddockm.link.cgi.UploadObserver;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPhase;
import com.jbraddockm.link.testing.TestResult;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ConversationRunner}: carrying out a conversation step by step against an
 * exchange.
 */
@DisplayName("ConversationRunner: the steps of a conversation, one after the other")
class ConversationRunnerTest {

  private static final CgiRequest ASKING = CgiRequest.Get.to(Endpoint.DYNAMIC_PROPERTIES);
  private static final CgiRequest ASKING_AGAIN = CgiRequest.Get.to(Endpoint.SUPPORT);

  /**
   * A conversation that starts with one step and continues with the steps it was given, recording
   * the answers.
   */
  private static final class Script implements Conversation<TestResult> {
    private final Step<TestResult> first;
    private final Deque<Step<TestResult>> later = new ArrayDeque<>();
    final List<CgiResponse> responses = new ArrayList<>();
    final List<ResponseEnvelope> envelopes = new ArrayList<>();

    Script(Step<TestResult> first) {
      this.first = first;
    }

    Script then(Step<TestResult> next) {
      later.add(next);
      return this;
    }

    @Override
    public Step<TestResult> start() {
      return first;
    }

    @Override
    public Step<TestResult> onAnswer(CgiResponse response, ResponseEnvelope envelope) {
      responses.add(response);
      envelopes.add(envelope);
      return later.poll();
    }
  }

  private static Step<TestResult> send(CgiRequest request) {
    return new Step.Send<>(request);
  }

  private static Step<TestResult> finish(String value) {
    return new Step.Finish<>(new TestResult(value));
  }

  private static CgiResponse answer(String resource) {
    return new CgiResponse(
        200, "text/xml", Resources.text(ConversationRunnerTest.class, resource), null);
  }

  private static <T extends CommandResult> List<String> rawOf(CommandOutcome<T> outcome) {
    return switch (outcome) {
      case CommandOutcome.Succeeded<T>(var _, var raw) -> raw;
      case CommandOutcome.Failed<T>(var _, var _, var raw) -> raw;
    };
  }

  /** An exchange that gives the same answer to every request and records the requests. */
  private static final class Answering implements ConversationRunner.Exchange {
    final List<CgiRequest> requests = new ArrayList<>();
    private final CgiResponse response;

    Answering(CgiResponse response) {
      this.response = response;
    }

    @Override
    public CgiResponse send(CgiRequest request, UploadObserver observer) {
      requests.add(request);
      return response;
    }
  }

  @Nested
  @DisplayName("how it ends")
  class Ending {

    @Test
    @DisplayName("a conversation that finishes at once succeeds without sending anything")
    void finishesAtOnce() throws Exception {
      var exchange = new Answering(answer("conversation-runner--answer.xml"));

      var outcome = ConversationRunner.run(new Script(finish("done")), exchange, new Logs());

      assertThat(succeeded(outcome)).isEqualTo(new TestResult("done"));
      assertThat(rawOf(outcome)).isEmpty();
      assertThat(exchange.requests).isEmpty();
    }

    @Test
    @DisplayName(
        "a conversation that fails at once fails with its error and what it had, without sending"
            + " anything")
    void failsAtOnce() throws Exception {
      var error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "no");
      var exchange = new Answering(answer("conversation-runner--answer.xml"));

      var outcome =
          failed(
              ConversationRunner.run(
                  new Script(new Step.Fail<>(error, Optional.of(new TestResult("half")))),
                  exchange,
                  new Logs()));

      assertThat(outcome.error()).isSameAs(error);
      assertThat(outcome.partialResult()).contains(new TestResult("half"));
      assertThat(exchange.requests).isEmpty();
    }

    @Test
    @DisplayName(
        "a request and a step after the answer: the request is sent once, and the conversation is"
            + " told the answer")
    void sendsAndFinishes() throws Exception {
      var exchange = new Answering(answer("conversation-runner--answer.xml"));
      var script = new Script(send(ASKING)).then(finish("done"));

      var outcome = ConversationRunner.run(script, exchange, new Logs());

      assertThat(exchange.requests).containsExactly(ASKING);
      assertThat(script.responses).hasSize(1);
      assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    }

    @Test
    @DisplayName("a failure after the answer ends the run with the error of the conversation")
    void failsAfterAnAnswer() throws Exception {
      var error = new ServiceError(CoreErrorCode.COMMAND_FAILED, "refused");
      var script = new Script(send(ASKING)).then(new Step.Fail<>(error));

      var outcome =
          failed(
              ConversationRunner.run(
                  script, new Answering(answer("conversation-runner--answer.xml")), new Logs()));

      assertThat(outcome.error()).isSameAs(error);
      assertThat(outcome.raw()).hasSize(1);
    }

    @Test
    @DisplayName("a failure of the exchange is not caught: the session decides what to do about it")
    void anExchangeThatFailsIsNotCaught() {
      ConversationRunner.Exchange broken =
          (_, _) -> {
            throw new IOException("connection reset");
          };

      assertThatThrownBy(() -> ConversationRunner.run(new Script(send(ASKING)), broken, new Logs()))
          .isInstanceOf(IOException.class)
          .hasMessage("connection reset");
    }
  }

  @Nested
  @DisplayName("what it keeps of the answers")
  class Keeping {

    @Test
    @DisplayName("the raw answers are kept in order, one for each exchange")
    void keepsTheRawAnswers() throws Exception {
      var answers =
          new ArrayDeque<>(
              List.of(
                  answer("conversation-runner--first.txt"),
                  answer("conversation-runner--answer.xml")));
      ConversationRunner.Exchange exchange = (_, _) -> answers.poll();
      var script = new Script(send(ASKING)).then(send(ASKING_AGAIN)).then(finish("done"));

      var outcome = ConversationRunner.run(script, exchange, new Logs());

      assertThat(rawOf(outcome))
          .containsExactly(
              Resources.text(ConversationRunnerTest.class, "conversation-runner--first.txt"),
              Resources.text(ConversationRunnerTest.class, "conversation-runner--answer.xml"));
    }

    @Test
    @DisplayName(
        "a secret in an answer is taken out of the raw answer, but the conversation is given the"
            + " answer as it was")
    void secretsAreTakenOutOfTheRawAnswer() throws Exception {
      var script = new Script(send(ASKING)).then(finish("done"));

      var outcome =
          ConversationRunner.run(
              script, new Answering(answer("conversation-runner--cookie-body.txt")), new Logs());

      assertThat(rawOf(outcome)).hasSize(1);
      assertThat(rawOf(outcome).getFirst()).contains("dtmauth=***").doesNotContain("abc123");
      assertThat(script.responses.getFirst().body()).contains("abc123");
    }

    @Test
    @DisplayName(
        "an identifier in an answer is taken out of the raw answer too, but the conversation is"
            + " given the answer as it was")
    void identifiersAreTakenOutOfTheRawAnswer() throws Exception {
      var script = new Script(send(ASKING)).then(finish("done"));

      var outcome =
          ConversationRunner.run(
              script,
              new Answering(answer("conversation-runner--identifiers-body.txt")),
              new Logs());

      assertThat(rawOf(outcome).getFirst()).contains("<PIN>***</PIN>").doesNotContain("2A3B4C5D");
      assertThat(script.responses.getFirst().body()).contains("2A3B4C5D");
    }

    @Test
    @DisplayName(
        "an answer that is XML is given to the conversation with its envelope, and one that is not,"
            + " without")
    void theEnvelopeIsGivenWhenThereIsOne() throws Exception {
      var xml = new Script(send(ASKING)).then(finish("x"));
      var text = new Script(send(ASKING)).then(finish("x"));

      ConversationRunner.run(
          xml, new Answering(answer("conversation-runner--answer.xml")), new Logs());
      ConversationRunner.run(
          text, new Answering(answer("conversation-runner--not-xml.txt")), new Logs());

      assertThat(xml.envelopes.getFirst()).isNotNull();
      assertThat(xml.envelopes.getFirst().name()).isEqualTo("DynamicProperties");
      assertThat(text.envelopes.getFirst()).isNull();
    }
  }

  @Nested
  @DisplayName("what it tells the listener")
  class Telling {

    @Test
    @DisplayName("says what each request does, once for a request that is repeated, as a poll is")
    void logsARepeatedRequestOnce() throws Exception {
      var logs = new Logs();
      var script =
          new Script(send(ASKING))
              .then(send(ASKING))
              .then(send(ASKING_AGAIN))
              .then(send(ASKING_AGAIN))
              .then(finish("done"));

      ConversationRunner.run(
          script, new Answering(answer("conversation-runner--answer.xml")), logs);

      assertThat(logs.messages(LogLevel.INFO))
          .containsExactly(ASKING.describe(), ASKING_AGAIN.describe());
    }

    @Test
    @DisplayName("says it again when the request comes back after another one")
    void logsARequestThatComesBack() throws Exception {
      var logs = new Logs();
      var script =
          new Script(send(ASKING)).then(send(ASKING_AGAIN)).then(send(ASKING)).then(finish("done"));

      ConversationRunner.run(
          script, new Answering(answer("conversation-runner--answer.xml")), logs);

      assertThat(logs.messages(LogLevel.INFO))
          .containsExactly(ASKING.describe(), ASKING_AGAIN.describe(), ASKING.describe());
    }

    @Test
    @DisplayName(
        "applies the effects of a step before it sends the request: notes, reports and pauses, in"
            + " order")
    void appliesTheEffectsFirst() throws Exception {
      var logs = new Logs();
      var progress = new Progress(40, TestPhase.UPLOADING, null, null, "package.bar");
      var effects =
          List.<StepEffect>of(
              new StepEffect.Note(LogLevel.WARN, "careful"),
              new StepEffect.Report(progress),
              new StepEffect.Pause(Duration.ofMillis(30)));
      var order = new ArrayList<String>();
      ConversationRunner.Exchange exchange =
          (_, _) -> {
            order.add("sent: " + logs.messages(LogLevel.WARN) + " " + logs.progress().size());
            return answer("conversation-runner--answer.xml");
          };
      var started = System.nanoTime();

      ConversationRunner.run(
          new Script(new Step.Send<>(ASKING, effects)).then(finish("done")), exchange, logs);

      assertThat(order).containsExactly("sent: [careful] 1");
      assertThat(logs.progress()).containsExactly(progress);
      assertThat(Duration.ofNanos(System.nanoTime() - started))
          .isGreaterThanOrEqualTo(Duration.ofMillis(25));
    }

    @Test
    @DisplayName(
        "a pause that is interrupted ends the run with an interruption, and the thread is still"
            + " marked as interrupted")
    void aPauseThatIsInterrupted() {
      var effects = List.<StepEffect>of(new StepEffect.Pause(Duration.ofSeconds(30)));
      Thread.currentThread().interrupt();
      try {
        assertThatThrownBy(
                () ->
                    ConversationRunner.run(
                        new Script(new Step.Send<>(ASKING, effects)),
                        new Answering(answer("conversation-runner--answer.xml")),
                        new Logs()))
            .isInstanceOf(InterruptedIOException.class)
            .hasMessage("The command was interrupted while it waited");
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
      } finally {
        Thread.interrupted();
      }
    }
  }

  @Nested
  @DisplayName("an upload")
  class Uploading {

    private final CgiRequest upload =
        CgiRequest.Upload.of(
            Endpoint.UPDATE, Path.of("package.bar"), 2048L, TestPhase.UPLOADING, "package.bar");

    @Test
    @DisplayName(
        "tells the progress of the file as the exchange counts its bytes: the percent, the bytes,"
            + " the total and the item")
    void reportsTheProgress() throws Exception {
      var logs = new Logs();
      ConversationRunner.Exchange exchange =
          (_, observer) -> {
            observer.sent(512);
            observer.sent(2048);
            return answer("conversation-runner--answer.xml");
          };

      ConversationRunner.run(new Script(send(upload)).then(finish("done")), exchange, logs);

      assertThat(logs.progress())
          .containsExactly(
              new Progress(25, TestPhase.UPLOADING, 512L, 2048L, "package.bar"),
              new Progress(100, TestPhase.UPLOADING, 2048L, 2048L, "package.bar"));
    }

    @Test
    @DisplayName("says 100 percent for a file of no bytes, which has nothing to divide by")
    void anEmptyFileIsComplete() throws Exception {
      var empty =
          CgiRequest.Upload.of(
              Endpoint.UPDATE, Path.of("empty.bar"), 0L, TestPhase.UPLOADING, "empty.bar");
      var logs = new Logs();
      ConversationRunner.Exchange exchange =
          (_, observer) -> {
            observer.sent(7);
            return answer("conversation-runner--answer.xml");
          };

      ConversationRunner.run(new Script(send(empty)).then(finish("done")), exchange, logs);

      assertThat(logs.progress()).hasSize(1);
      assertThat(logs.progress().getFirst().percent()).isEqualTo(100);
    }

    @Test
    @DisplayName("gives a request that is not an upload an observer that does nothing")
    void otherRequestsHaveNoObserver() throws Exception {
      var logs = new Logs();
      ConversationRunner.Exchange exchange =
          (_, observer) -> {
            observer.sent(99);
            return answer("conversation-runner--answer.xml");
          };

      ConversationRunner.run(new Script(send(ASKING)).then(finish("done")), exchange, logs);

      assertThat(logs.progress()).isEmpty();
    }
  }

  @Nested
  @DisplayName("a session that the device no longer knows")
  class Stale {

    @Test
    @DisplayName("on the first exchange is told as such: the login may be renewed")
    void staleOnTheFirstExchange() {
      var refusing = new Answering(new CgiResponse(401, "text/html", "", null));

      assertThatThrownBy(
              () ->
                  ConversationRunner.run(
                      new Script(send(ASKING)).then(finish("x")), refusing, new Logs()))
          .isInstanceOfSatisfying(
              StaleSessionException.class, stale -> assertThat(stale.firstExchange()).isTrue());
    }

    @Test
    @DisplayName(
        "after an exchange that went well is told as such too: the session was lost in the middle"
            + " of the command")
    void staleInTheMiddle() {
      var answers =
          new ArrayDeque<>(
              List.of(
                  answer("conversation-runner--answer.xml"),
                  new CgiResponse(403, "text/html", "", null)));
      ConversationRunner.Exchange exchange = (_, _) -> answers.poll();

      assertThatThrownBy(
              () ->
                  ConversationRunner.run(
                      new Script(send(ASKING)).then(send(ASKING_AGAIN)).then(finish("x")),
                      exchange,
                      new Logs()))
          .isInstanceOfSatisfying(
              StaleSessionException.class, stale -> assertThat(stale.firstExchange()).isFalse());
    }

    @Test
    @DisplayName(
        "is found before the conversation is told the answer: the conversation never sees an answer"
            + " that says the session is lost")
    void theConversationIsNotToldAStaleAnswer() {
      var script = new Script(send(ASKING)).then(finish("x"));

      assertThatThrownBy(
              () ->
                  ConversationRunner.run(
                      script,
                      new Answering(new CgiResponse(401, "text/html", "", null)),
                      new Logs()))
          .isInstanceOf(StaleSessionException.class);
      assertThat(script.responses).isEmpty();
    }

    @Test
    @DisplayName("is not what a request that expects no body gets when the answer is empty")
    void anExpectedEmptyAnswerIsNotStale() throws Exception {
      var quiet = CgiRequest.Get.to(Endpoint.PIM_SYNC).expectingNoBody();

      var outcome =
          ConversationRunner.run(
              new Script(send(quiet)).then(finish("done")),
              new Answering(new CgiResponse(200, null, "", null)),
              new Logs());

      assertThat(outcome).isInstanceOf(CommandOutcome.Succeeded.class);
    }
  }
}
