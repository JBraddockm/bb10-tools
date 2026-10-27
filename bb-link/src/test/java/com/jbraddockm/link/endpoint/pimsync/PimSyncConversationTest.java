package com.jbraddockm.link.endpoint.pimsync;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.partial;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.transport.cgi.BodyFormat;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link PimSyncConversation}: how an answer of {@code pimsync.cgi} is kept and
 * classified.
 */
@DisplayName("PimSyncConversation: the answer of the PIM program of the device")
class PimSyncConversationTest {

  private static Step<PimAnswer> answer(String resource, int httpStatus) {
    var body = Resources.text(PimSyncConversationTest.class, resource);
    var conversation =
        new PimSyncConversation(
            PimCommand.LIST_ACCOUNTS, null, "Listing the PIM accounts", UnaryOperator.identity());
    return conversation.onAnswer(new CgiResponse(httpStatus, "application/json", body, null), null);
  }

  private static Step<PimAnswer> answer(String resource) {
    return answer(resource, 200);
  }

  @Nested
  @DisplayName("the request")
  class Request {

    @Test
    @DisplayName("is a GET to pimsync.cgi with the command, and says what it is for")
    void isAGetWithTheCommand() {
      var conversation =
          new PimSyncConversation(
              PimCommand.FIND_ACCOUNT_ID, null, "Looking for an account", UnaryOperator.identity());

      var request = (CgiRequest.Get) sent(conversation.start());

      assertThat(request.pathAndQuery()).isEqualTo("/cgi-bin/pimsync.cgi?command=find_accountId");
      assertThat(request.describe()).isEqualTo("Looking for an account");
    }

    @Test
    @DisplayName(
        "may be answered with nothing: an empty answer is an answer, and not a lost session")
    void mayBeAnsweredWithNothing() {
      var conversation =
          new PimSyncConversation(PimCommand.LIST_ACCOUNTS, null, "x", UnaryOperator.identity());

      assertThat(sent(conversation.start()).emptyAnswerIsNormal()).isTrue();
    }

    @Test
    @DisplayName("has the parameters that the command adds, after the command")
    void hasTheParametersOfTheCommand() {
      var conversation =
          new PimSyncConversation(
              PimCommand.GET,
              PimService.EVENTS,
              "x",
              request -> request.withText(QueryParameter.LIMIT, "5"));

      assertThat(((CgiRequest.Get) sent(conversation.start())).pathAndQuery())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=get&limit=5");
    }
  }

  @Nested
  @DisplayName("an answer that is not a refusal")
  class Answers {

    @Test
    @DisplayName(
        "is kept with what it said: the command, the status of HTTP, the content type, the code,"
            + " the message and the body")
    void isKept() {
      var result = finished(answer("pim--accounts.json"));

      assertThat(result.command()).isEqualTo(PimCommand.LIST_ACCOUNTS);
      assertThat(result.service()).isNull();
      assertThat(result.httpStatus()).isEqualTo(200);
      assertThat(result.contentType()).isEqualTo("application/json");
      assertThat(result.code()).isEqualTo(0);
      assertThat(result.message()).isEqualTo("OK");
      assertThat(result.body().format()).isEqualTo(BodyFormat.JSON);
    }

    @Test
    @DisplayName("keeps the service that the command was for")
    void keepsTheService() {
      var conversation =
          new PimSyncConversation(
              PimCommand.GET, PimService.CONTACTS, "x", UnaryOperator.identity());
      var body = Resources.text(PimSyncConversationTest.class, "pim--ok.json");

      var result =
          finished(
              conversation.onAnswer(new CgiResponse(200, "application/json", body, null), null));

      assertThat(result.service()).isEqualTo(PimService.CONTACTS);
    }

    @Test
    @DisplayName(
        "with another code than the three of refusal is an answer, with its code and its message")
    void anotherCodeIsAnAnswer() {
      var result = finished(answer("pim--other-error.json"));

      assertThat(result.code()).isEqualTo(2000);
      assertThat(result.message()).isEqualTo("something else went wrong");
    }

    @Test
    @DisplayName("with no status is an answer, with no code and no message")
    void noStatusIsAnAnswer() {
      var result = finished(answer("pim--no-status.json"));

      assertThat(result.code()).isNull();
      assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("that is empty is an answer too: a text of nothing, with no code")
    void anEmptyAnswer() {
      var result = finished(answer("pim--empty.txt"));

      assertThat(result.body().format()).isEqualTo(BodyFormat.TEXT);
      assertThat(result.body().value()).isEqualTo("");
      assertThat(result.code()).isNull();
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"pim--xml.xml, XML", "pim--text.txt, TEXT", "pim--accounts.json, JSON"})
    @DisplayName("is kept in the form that it has: XML, JSON, or plain text")
    void isKeptInItsForm(String resource, BodyFormat expected) {
      assertThat(finished(answer(resource)).body().format()).isEqualTo(expected);
    }

    @Test
    @DisplayName("keeps the status of HTTP that came, though it is not 200")
    void keepsTheHttpStatus() {
      assertThat(finished(answer("pim--ok.json", 500)).httpStatus()).isEqualTo(500);
    }
  }

  @Nested
  @DisplayName("a refusal")
  class Refusals {

    @ParameterizedTest(name = "{0}: code {1}")
    @CsvSource({
      "pim--invalid-parameters.json, 1001, invalid parameter workstation_id",
      "pim--unauthorized.json, 1013, received HTTP 401/403 from server",
      "pim--unsupported.json, 1015, unsupported service"
    })
    @DisplayName("fails the command with the words of the device and its code, and keeps what came")
    void failsWithTheWordsOfTheDevice(String resource, int code, String message) {
      var step = answer(resource);

      var error = failed(step);
      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).isEqualTo(message);
      assertThat(error.details()).isEqualTo(Map.of("code", code));
      assertThat(partial(step)).hasValueSatisfying(kept -> assertThat(kept.code()).isEqualTo(code));
    }

    @Test
    @DisplayName(
        "of a device with no session, which says so in text that is not JSON, is a refusal all the"
            + " same")
    void aSessionThatIsLost() {
      var step = answer("pim--not-logged-in.txt");

      assertThat(failed(step).message()).isEqualTo("received HTTP 401/403 from server");
      assertThat(partial(step))
          .hasValueSatisfying(kept -> assertThat(kept.body().format()).isEqualTo(BodyFormat.TEXT));
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"pim--rejected-without-message.json", "pim--rejected-blank-message.json"})
    @DisplayName(
        "with no message, or a blank one, says in its own words that the device rejected the"
            + " request")
    void withoutAMessage(String resource) {
      assertThat(failed(answer(resource)).message()).isEqualTo("The device rejected the request");
    }
  }
}
