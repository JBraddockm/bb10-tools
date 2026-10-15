package com.jbraddockm.link.session;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.xml.MalformedXmlException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Optional;

/** Runs a conversation: sends what it asks for and hands every answer back to it. */
final class ConversationRunner {

  private ConversationRunner() {}

  /** Sends a request and returns the device's answer. */
  @FunctionalInterface
  interface Exchange {
    CgiResponse send(CgiRequest request) throws IOException;
  }

  static <R extends CommandResult> CommandOutcome<R> run(
      Conversation<R> conversation, Exchange exchange, CommandListener listener)
      throws IOException, StaleSessionException {
    var raw = new ArrayList<String>();
    var exchanges = 0;
    var step = conversation.start();
    while (true) {
      switch (step) {
        case Step.Finish<R>(var result) -> {
          return new CommandOutcome.Succeeded<>(result, raw);
        }
        case Step.Fail<R>(var error) -> {
          return new CommandOutcome.Failed<>(error, Optional.empty(), raw);
        }
        case Step.Send<R>(var request) -> {
          listener.onLog(LogLevel.INFO, request.describe());
          var response = exchange.send(request);
          raw.add(Scrub.secrets(response.body()));
          var envelope = parse(response);
          if (StaleSession.indicatedBy(request, response, envelope)) {
            throw new StaleSessionException(exchanges == 0);
          }
          exchanges++;
          if (envelope.isEmpty()) {
            return new CommandOutcome.Failed<>(notXml(response), Optional.empty(), raw);
          }
          step = conversation.onResponse(envelope.get());
        }
      }
    }
  }

  private static Optional<ResponseEnvelope> parse(CgiResponse response) {
    try {
      return Optional.of(ResponseEnvelope.parse(response.body()));
    } catch (MalformedXmlException _) {
      return Optional.empty();
    }
  }

  private static ServiceError notXml(CgiResponse response) {
    return new ServiceError(
        LinkErrorCode.UNEXPECTED_RESPONSE,
        "The device answered with something that is not XML (HTTP " + response.statusCode() + ")",
        Json.object("status", response.statusCode(), "contentType", response.contentType()));
  }
}
