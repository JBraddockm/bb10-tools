package com.jbraddockm.daemon;

import com.jbraddockm.DeployError;
import com.jbraddockm.device.CommandListener;
import com.jbraddockm.device.CommandOutcome;
import com.jbraddockm.internal.Json;
import com.jbraddockm.output.CommandResult;
import com.jbraddockm.output.LogLevel;
import com.jbraddockm.output.Progress;
import java.util.List;
import java.util.Map;

/**
 * Everything sent in answer to one request. A request gets any number of log and progress events,
 * at most one result, and exactly one final event ({@link #succeed()} or {@link #fail}).
 */
final class Reply implements CommandListener {

  private final EventWriter writer;
  private final Object idJson;

  private Reply(EventWriter writer, RequestId id) {
    this.writer = writer;
    this.idJson = id == null ? Json.NULL : id.toJson();
  }

  /**
   * A reply to the request with this id, or, if the id is {@code null}, to a request without a
   * usable id.
   */
  static Reply to(EventWriter writer, RequestId id) {
    return new Reply(writer, id);
  }

  @Override
  public void onLog(LogLevel level, String message) {
    send(Json.object("id", idJson, "type", "log", "level", level.wireName(), "message", message));
  }

  @Override
  public void onProgress(Progress progress) {
    send(
        Json.object(
            "id",
            idJson,
            "type",
            "progress",
            "percent",
            progress.percent(),
            "phase",
            progress.phase().wireName()));
  }

  /**
   * Sends the typed result of a command.
   *
   * @param command the method name
   * @param data the result's JSON view
   * @param raw the unparsed output lines, or {@code null} to omit them
   */
  void result(String command, Map<String, Object> data, List<String> raw) {
    send(Json.object("id", idJson, "type", "result", "command", command, "data", data, "raw", raw));
  }

  void succeed() {
    send(Json.object("id", idJson, "type", "done", "ok", true));
  }

  void fail(DeployError error) {
    send(Json.object("id", idJson, "type", "done", "ok", false, "error", error.toJson()));
  }

  /**
   * Sends the events that end a command: the result if there is one, then the final event.
   *
   * <p>If the parser itself failed, the data is empty and the raw lines carry the information.
   */
  <T extends CommandResult> void complete(String command, CommandOutcome<T> outcome) {
    switch (outcome) {
      case CommandOutcome.Succeeded<T>(var result, var rawLines) -> {
        result(command, result.map(CommandResult::toJson).orElseGet(Map::of), rawLines);
        succeed();
      }
      case CommandOutcome.Failed<T>(var error, var partialResult, var rawLines) -> {
        partialResult.ifPresent(partial -> result(command, partial.toJson(), rawLines));
        fail(error);
      }
    }
  }

  private void send(Map<String, Object> event) {
    writer.write(event);
  }
}
