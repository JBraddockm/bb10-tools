package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link XmlConversation}: an XML answer reaches the conversation; any other answer ends
 * it with an error.
 */
@DisplayName("XmlConversation: a conversation that reads XML answers")
class XmlConversationTest {

  /** A conversation that sends one request and finishes with the name of the root of the answer. */
  private static final class NameOfTheAnswer extends XmlConversation<TestResult> {

    @Override
    public Step<TestResult> start() {
      return new Step.Send<>(CgiRequest.Get.to(Endpoint.SUPPORT));
    }

    @Override
    protected Step<TestResult> onResponse(ResponseEnvelope response) {
      return new Step.Finish<>(new TestResult(response.name()));
    }
  }

  @Test
  @DisplayName("an answer that is XML goes to onResponse, and what it gives is the next step")
  void anXmlAnswerGoesToTheConversation() {
    var envelope =
        ResponseEnvelope.parse(
            Resources.text(XmlConversationTest.class, "response-envelope--known-element.xml"));

    var step = new NameOfTheAnswer().onAnswer(new CgiResponse(200, "text/xml", "", null), envelope);

    assertThat(step)
        .isInstanceOfSatisfying(
            Step.Finish.class,
            finish -> assertThat(finish.result()).isEqualTo(new TestResult("DevicePIN")));
  }

  @Test
  @DisplayName(
      "an answer that is not XML fails the conversation, whatever the conversation would have done")
  void anAnswerThatIsNotXmlFails() {
    var step = new NameOfTheAnswer().onAnswer(htmlError(), null);

    assertThat(step)
        .isInstanceOfSatisfying(
            Step.Fail.class,
            fail -> {
              assertThat(fail.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
              assertThat(fail.error().message()).contains("HTTP 502");
            });
  }

  @Test
  @DisplayName("start is what the conversation says it is")
  void startIsTheFirstRequest() {
    assertThat(new NameOfTheAnswer().start()).isInstanceOf(Step.Send.class);
  }

  private static CgiResponse htmlError() {
    return new CgiResponse(
        502,
        "text/html",
        Resources.text(XmlConversationTest.class, "cgi-response--html-error.html"),
        null);
  }
}
