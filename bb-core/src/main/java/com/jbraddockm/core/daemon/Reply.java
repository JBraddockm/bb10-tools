package com.jbraddockm.core.daemon;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Everything a command may send back to the client for one request: log and progress events, any
 * number of custom events, at most one result and exactly one final {@code done} event.
 *
 * <p>The first of {@link #succeed()} and {@link #fail(ServiceError)} wins; later calls are ignored,
 * so a request can never be answered twice.
 */
public final class Reply implements CommandListener {

  private final EventWriter writer;
  private final Object idJson;
  private final AtomicBoolean finished = new AtomicBoolean();

  private Reply(EventWriter writer, Object idJson) {
    this.writer = writer;
    this.idJson = idJson;
  }

  static Reply to(EventWriter writer, RequestId id) {
    return new Reply(writer, id == null ? Json.NULL : id.toJson());
  }

  /**
   * Creates a reply that writes to a stream outside the daemon, for example to replay recorded
   * output. The events carry the given numeric id.
   *
   * @param stream the stream receiving one JSON object per line
   * @param id the id echoed in every event
   * @return the reply
   */
  public static Reply standalone(PrintStream stream, long id) {
    return new Reply(new EventWriter(stream), id);
  }

  @Override
  public void onLog(LogLevel level, String message) {
    writer.write(
        Json.object("id", idJson, "type", "log", "level", level.wireName(), "message", message));
  }

  @Override
  public void onProgress(Progress progress) {
    var event = Json.object("id", idJson, "type", "progress");
    event.putAll(progress.toJson());
    writer.write(event);
  }

  /**
   * Sends a custom event, for example one device found while a scan is still running. Clients
   * ignore event types they do not know.
   *
   * @param type the event type; must not be one of the reserved types
   * @param fields the event's fields; must not contain {@code id} or {@code type}
   * @throws IllegalArgumentException if a reserved name is used
   */
  public void event(String type, Map<String, Object> fields) {
    if (List.of("log", "progress", "result", "done", "hello").contains(type)
        || fields.containsKey("id")
        || fields.containsKey("type")) {
      throw new IllegalArgumentException("reserved event type or field");
    }
    var event = Json.object("id", idJson, "type", type);
    event.putAll(fields);
    writer.write(event);
  }

  /**
   * Sends the result of the request.
   *
   * @param command the method that produced it
   * @param data the result data as a JSON-compatible tree
   * @param raw scrubbed device output to include, or {@code null} to omit it
   */
  public void result(String command, Map<String, Object> data, List<String> raw) {
    writer.write(
        Json.object("id", idJson, "type", "result", "command", command, "data", data, "raw", raw));
  }

  /** Ends the request successfully. */
  public void succeed() {
    if (finished.compareAndSet(false, true)) {
      writer.write(Json.object("id", idJson, "type", "done", "ok", true));
    }
  }

  /**
   * Ends the request with an error.
   *
   * @param error what went wrong
   */
  public void fail(ServiceError error) {
    if (finished.compareAndSet(false, true)) {
      writer.write(Json.object("id", idJson, "type", "done", "ok", false, "error", error.toJson()));
    }
  }

  /**
   * Sends the result, if any, and the final event for a command outcome.
   *
   * @param command the method that produced the outcome
   * @param outcome how the command ended
   * @param includeRaw whether to attach the scrubbed device output to the result
   * @param <T> the result type
   */
  public <T extends CommandResult> void complete(
      String command, CommandOutcome<T> outcome, boolean includeRaw) {
    switch (outcome) {
      case CommandOutcome.Succeeded<T>(var result, var raw) -> {
        result(command, result.toJson(), includeRaw ? raw : null);
        succeed();
      }
      case CommandOutcome.Failed<T>(var error, var partial, var raw) -> {
        partial.ifPresent(result -> result(command, result.toJson(), includeRaw ? raw : null));
        fail(error);
      }
    }
  }

  /**
   * Tells whether the final event has been sent.
   *
   * @return {@code true} after {@link #succeed()} or {@link #fail(ServiceError)}
   */
  boolean isFinished() {
    return finished.get();
  }
}
