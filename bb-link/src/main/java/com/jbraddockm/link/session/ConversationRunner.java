package com.jbraddockm.link.session;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.StepEffect;
import com.jbraddockm.link.cgi.UploadObserver;
import com.jbraddockm.link.internal.Scrub;
import com.jbraddockm.link.xml.MalformedXmlException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.ArrayList;
import java.util.Optional;

/** Runs a conversation: sends what it asks for and hands every answer back to it. */
final class ConversationRunner {

  private ConversationRunner() {}

  /** Sends a request and returns the device's answer. */
  @FunctionalInterface
  interface Exchange {
    CgiResponse send(CgiRequest request, UploadObserver observer) throws IOException;
  }

  static <R extends CommandResult> CommandOutcome<R> run(
      Conversation<R> conversation, Exchange exchange, CommandListener listener)
      throws IOException, StaleSessionException {
    var raw = new ArrayList<String>();
    var exchanges = 0;
    var step = conversation.start();
    String lastDescribed = null;
    while (true) {
      switch (step) {
        case Step.Finish<R>(var result) -> {
          return new CommandOutcome.Succeeded<>(result, raw);
        }
        case Step.Fail<R>(var error, var partial) -> {
          return new CommandOutcome.Failed<>(error, partial, raw);
        }
        case Step.Send<R>(var request, var before) -> {
          for (var effect : before) {
            apply(effect, listener);
          }
          // A request that is repeated, such as a poll, is logged the first time only.
          if (!request.describe().equals(lastDescribed)) {
            listener.onLog(LogLevel.INFO, request.describe());
            lastDescribed = request.describe();
          }
          var response = exchange.send(request, observerFor(request, listener));
          raw.add(Scrub.secrets(response.body()));
          var envelope = parse(response);
          if (StaleSession.indicatedBy(request, response, envelope)) {
            throw new StaleSessionException(exchanges == 0);
          }
          exchanges++;
          step = conversation.onAnswer(response, envelope);
        }
      }
    }
  }

  /** Does what a conversation asked to be done before its next request. */
  private static void apply(StepEffect effect, CommandListener listener)
      throws InterruptedIOException {
    switch (effect) {
      case StepEffect.Report(var progress) -> listener.onProgress(progress);
      case StepEffect.Note(var level, var message) -> listener.onLog(level, message);
      case StepEffect.Pause(var duration) -> {
        try {
          Thread.sleep(duration);
        } catch (InterruptedException _) {
          Thread.currentThread().interrupt();
          throw new InterruptedIOException("The command was interrupted while it waited");
        }
      }
    }
  }

  /** Turns the bytes of an upload that have gone out into progress events. */
  private static UploadObserver observerFor(CgiRequest request, CommandListener listener) {
    if (request instanceof CgiRequest.Upload upload) {
      return bytes ->
          listener.onProgress(
              new Progress(
                  upload.length() == 0 ? 100 : (int) (bytes * 100 / upload.length()),
                  upload.phase(),
                  bytes,
                  upload.length(),
                  upload.item()));
    }
    return UploadObserver.NONE;
  }

  private static Optional<ResponseEnvelope> parse(CgiResponse response) {
    try {
      return Optional.of(ResponseEnvelope.parse(response.body()));
    } catch (MalformedXmlException _) {
      return Optional.empty();
    }
  }
}
