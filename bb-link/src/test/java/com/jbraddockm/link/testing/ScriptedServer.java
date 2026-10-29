package com.jbraddockm.link.testing;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

/**
 * A loopback HTTP server that gives scripted answers in order and records the requests it receives.
 */
public final class ScriptedServer implements AutoCloseable {

  /**
   * One request as it arrived.
   *
   * @param method the HTTP method
   * @param target the path and the query, as sent
   * @param headers the headers by name in small letters
   * @param body the bytes of the body
   */
  public record Request(
      String method, String target, Map<String, List<String>> headers, byte[] body) {

    /**
     * The first value of a header.
     *
     * @param name the name, in any case
     * @return the value, or {@code null} if the request had none
     */
    public String header(String name) {
      var values = headers.get(name.toLowerCase(Locale.ROOT));
      return values == null || values.isEmpty() ? null : values.getFirst();
    }

    /**
     * The body as text.
     *
     * @return the body, read as UTF-8
     */
    public String bodyText() {
      return new String(body, StandardCharsets.UTF_8);
    }
  }

  private record Answer(
      int status, Map<String, String> headers, byte[] body, Duration delay, boolean drop) {}

  private final HttpServer server;
  private final Queue<Answer> answers = new ConcurrentLinkedQueue<>();
  private final List<Request> requests = Collections.synchronizedList(new ArrayList<>());

  /**
   * Starts the server on a free port.
   *
   * @throws IOException if it cannot be started
   */
  public ScriptedServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.setExecutor(
        Executors.newCachedThreadPool(
            runnable -> Thread.ofPlatform().daemon().unstarted(runnable)));
    server.createContext("/", this::handle);
    server.start();
  }

  /**
   * Queues an answer.
   *
   * @param status the HTTP status
   * @param body the body, as text; empty for no body
   * @param headers pairs of header names and values
   */
  public void answer(int status, String body, String... headers) {
    answers.add(
        new Answer(
            status, pairs(headers), body.getBytes(StandardCharsets.UTF_8), Duration.ZERO, false));
  }

  /**
   * Queues an answer that is bytes and not text.
   *
   * @param status the HTTP status
   * @param body the bytes of the body
   * @param headers pairs of header names and values
   */
  public void answerBytes(int status, byte[] body, String... headers) {
    answers.add(new Answer(status, pairs(headers), body.clone(), Duration.ZERO, false));
  }

  /**
   * Queues an answer that comes after a delay.
   *
   * @param delay how long the server waits before it answers
   * @param body the body, as text
   */
  public void answerAfter(Duration delay, String body) {
    answers.add(new Answer(200, Map.of(), body.getBytes(StandardCharsets.UTF_8), delay, false));
  }

  /**
   * Queues a request that is not answered: the server closes the connection when it has read it.
   */
  public void dropConnection() {
    answers.add(new Answer(0, Map.of(), new byte[0], Duration.ZERO, true));
  }

  /**
   * The port that the server is on.
   *
   * @return the port
   */
  public int port() {
    return server.getAddress().getPort();
  }

  /**
   * The requests that arrived, in order.
   *
   * @return a copy
   */
  public List<Request> requests() {
    synchronized (requests) {
      return List.copyOf(requests);
    }
  }

  private static Map<String, String> pairs(String... headers) {
    var result = new LinkedHashMap<String, String>();
    for (var index = 0; index + 1 < headers.length; index += 2) {
      result.put(headers[index], headers[index + 1]);
    }
    return result;
  }

  private void handle(HttpExchange exchange) throws IOException {
    var headers = new LinkedHashMap<String, List<String>>();
    exchange
        .getRequestHeaders()
        .forEach((name, values) -> headers.put(name.toLowerCase(Locale.ROOT), List.copyOf(values)));
    var body = exchange.getRequestBody().readAllBytes();
    requests.add(
        new Request(
            exchange.getRequestMethod(), exchange.getRequestURI().toString(), headers, body));
    var answer = answers.poll();
    if (answer == null) {
      exchange.sendResponseHeaders(500, -1);
      exchange.close();
      return;
    }
    if (answer.drop()) {
      exchange.close();
      return;
    }
    try {
      Thread.sleep(answer.delay());
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
    answer.headers().forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
    exchange.sendResponseHeaders(
        answer.status(), answer.body().length == 0 ? -1 : answer.body().length);
    if (answer.body().length > 0) {
      exchange.getResponseBody().write(answer.body());
    }
    exchange.close();
  }

  @Override
  public void close() {
    server.stop(0);
  }
}
