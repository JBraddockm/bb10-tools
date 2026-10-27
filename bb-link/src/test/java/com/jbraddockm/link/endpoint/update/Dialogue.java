package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import java.util.Map;

/**
 * Drives a conversation with answers read from resource templates, exposing the step that each
 * answer produces.
 *
 * @param <R> the kind of result of the conversation
 */
final class Dialogue<R extends CommandResult> {

  private final Conversation<R> conversation;

  Dialogue(Conversation<R> conversation) {
    this.conversation = conversation;
  }

  Step<R> start() {
    return conversation.start();
  }

  /** The device answers with a file, whose placeholders take the values that are given. */
  Step<R> answer(String resource, Map<String, String> values) {
    var body = Resources.text(Dialogue.class, resource, values);
    return conversation.onAnswer(
        new CgiResponse(200, "text/xml", body, null), ResponseEnvelope.parse(body));
  }

  /** The device answers with an element that has this status. */
  Step<R> answer(String element, String status) {
    return answer("update--answer.xml", Map.of("element", element, "status", status));
  }

  /** The device answers with an element that has this status and this progress. */
  Step<R> answer(String element, String status, String progress) {
    return answer(
        "update--answer-with-progress.xml",
        Map.of("element", element, "status", status, "progress", progress));
  }

  /** The device answers with an element that has no status. */
  Step<R> answerWithoutStatus(String element) {
    return answer("update--answer-without-status.xml", Map.of("element", element));
  }

  /** The device answers with an element that carries an error. */
  Step<R> answerWithError(String element) {
    return answer("update--answer-with-error.xml", Map.of("element", element));
  }

  /** The device answers with the properties of a device that has this much free space. */
  Step<R> answerWithFreeSpace(long free) {
    return answer("update--properties.xml", Map.of("free", String.valueOf(free)));
  }

  /**
   * The device answers with something that is not XML, from a file; an empty file is no answer at
   * all.
   */
  Step<R> answerWithText(String resource) {
    var body = Resources.text(Dialogue.class, resource);
    return conversation.onAnswer(new CgiResponse(200, "text/html", body, null), null);
  }
}
