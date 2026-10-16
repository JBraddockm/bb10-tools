package com.jbraddockm.link.cgi;

import com.jbraddockm.core.command.ProgressPhase;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.SequencedMap;
import java.util.stream.Collectors;

/** A request that a conversation asks the session to send to the device. */
public sealed interface CgiRequest {

  /**
   * Returns the CGI program the request goes to.
   *
   * @return the endpoint
   */
  Endpoint endpoint();

  /**
   * Describes the request for log messages by what it does, not by the name of the device's CGI
   * program, which can be misleading: the PIN is read through a program called {@code wipe.cgi}.
   * Values are never included.
   *
   * @return the purpose, for example {@code Reading the device PIN}; a request without a purpose is
   *     described as {@code POST} and the program's file name
   */
  String describe();

  /**
   * Tells whether an empty answer is a normal way for the device to say "done".
   *
   * <p>A device that does not accept the session also answers with an empty body and status 200. So
   * an empty answer is taken as "the session is gone" unless the request is known to be answered
   * with nothing, as a reboot probably is. Getting this wrong for a command that changes the device
   * would send it twice.
   *
   * @return {@code true} if an empty answer means success
   */
  boolean emptyAnswerIsNormal();

  /** How long the device may stay silent before a request is given up. */
  Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);

  /**
   * Returns how long the device may stay silent while the request is being answered. Most requests
   * are answered at once; an installation is not, and asks for more.
   *
   * @return the longest silence that is waited for
   */
  default Duration readTimeout() {
    return DEFAULT_READ_TIMEOUT;
  }

  /**
   * A GET, optionally with a query string.
   *
   * @param endpoint the CGI program
   * @param query the query parameters in the order they are sent
   * @param purpose what the request does, in a few words, for log messages; may be empty
   * @param emptyAnswerIsNormal see {@link CgiRequest#emptyAnswerIsNormal()}
   */
  record Get(
      Endpoint endpoint,
      SequencedMap<QueryParameter, String> query,
      String purpose,
      boolean emptyAnswerIsNormal)
      implements CgiRequest {

    /** Takes an immutable copy of the query. */
    public Get {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(purpose, "purpose");
      query = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(query));
    }

    /**
     * Starts a GET without parameters. An empty answer to it means a lost session.
     *
     * @param endpoint the CGI program
     * @return a GET without a query
     */
    public static Get to(Endpoint endpoint) {
      return new Get(endpoint, new LinkedHashMap<>(), "", false);
    }

    /**
     * Adds a parameter whose value is one of the device's fixed words.
     *
     * @param parameter the parameter
     * @param value the value
     * @return a new request with the parameter appended
     */
    public Get with(QueryParameter parameter, WireValue value) {
      return withText(parameter, value.wireName());
    }

    /**
     * Adds a parameter with free text as value.
     *
     * @param parameter the parameter
     * @param text the value
     * @return a new request with the parameter appended
     */
    public Get withText(QueryParameter parameter, String text) {
      var copy = new LinkedHashMap<>(query);
      copy.put(parameter, text);
      return new Get(endpoint, copy, purpose, emptyAnswerIsNormal);
    }

    /**
     * Says what the request does, for log messages.
     *
     * @param what a few words in the present participle, for example {@code Reading the contacts}
     * @return a new request with the purpose set
     */
    public Get describedAs(String what) {
      return new Get(endpoint, query, what, emptyAnswerIsNormal);
    }

    /**
     * Marks the request as one whose empty answer is a normal answer, not a lost session.
     *
     * @return a new request that does not treat an empty answer as a lost session
     */
    public Get expectingNoBody() {
      return new Get(endpoint, query, purpose, true);
    }

    /**
     * Returns the path of the request with its query string.
     *
     * @return for example {@code /cgi-bin/pimsync.cgi?command=list_accounts}
     */
    public String pathAndQuery() {
      if (query.isEmpty()) {
        return endpoint.path();
      }
      return endpoint.path()
          + '?'
          + query.entrySet().stream()
              .map(
                  entry ->
                      FormEncoding.encode(entry.getKey().wireName())
                          + '='
                          + FormEncoding.encode(entry.getValue()))
              .collect(Collectors.joining("&"));
    }

    @Override
    public String describe() {
      return purpose.isEmpty() ? "GET " + endpoint.fileName() : purpose;
    }
  }

  /**
   * A form post.
   *
   * @param endpoint the CGI program
   * @param fields the fields in the order they are sent
   * @param purpose what the request is for, in a few words, for log messages; may be empty
   * @param emptyAnswerIsNormal see {@link CgiRequest#emptyAnswerIsNormal()}
   * @param readTimeout how long the device may stay silent before the request is given up
   */
  record Form(
      Endpoint endpoint,
      SequencedMap<FormField, String> fields,
      String purpose,
      boolean emptyAnswerIsNormal,
      Duration readTimeout)
      implements CgiRequest {

    /** Takes an immutable copy of the fields. */
    public Form {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(purpose, "purpose");
      fields = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(fields));
    }

    /**
     * Starts a form without fields.
     *
     * @param endpoint the CGI program
     * @return an empty form
     */
    public static Form to(Endpoint endpoint) {
      return new Form(endpoint, new LinkedHashMap<>(), "", false, DEFAULT_READ_TIMEOUT);
    }

    /**
     * Adds a field whose value is one of the device's fixed words.
     *
     * @param field the field
     * @param value the value
     * @return a new form with the field appended
     */
    public Form with(FormField field, WireValue value) {
      return withText(field, value.wireName());
    }

    /**
     * Adds a field with free text as value.
     *
     * @param field the field
     * @param text the value
     * @return a new form with the field appended
     */
    public Form withText(FormField field, String text) {
      var copy = new LinkedHashMap<>(fields);
      copy.put(field, text);
      return new Form(endpoint, copy, purpose, emptyAnswerIsNormal, readTimeout);
    }

    /**
     * Adds a field whose value repeats its own name, which is how the device is asked for its
     * properties.
     *
     * @param field the field
     * @return a new form with the field appended
     */
    public Form echo(FormField field) {
      return with(field, field);
    }

    /**
     * Says what the request does. Log messages show this instead of the program's name, so that a
     * request to a program with an alarming name, such as the one that also reads the PIN, is not
     * misread.
     *
     * @param what a few words in the present participle, for example {@code Reading the device PIN}
     * @return a new form with the purpose set
     */
    public Form describedAs(String what) {
      return new Form(endpoint, fields, what, emptyAnswerIsNormal, readTimeout);
    }

    /**
     * Marks the request as one the device answers with nothing when it succeeds.
     *
     * @return a new form that does not treat an empty answer as a lost session
     */
    public Form expectingNoBody() {
      return new Form(endpoint, fields, purpose, true, readTimeout);
    }

    /**
     * Sets how long the device may stay silent before the request is given up.
     *
     * @param longest the longest silence that is waited for
     * @return the form with that limit
     */
    public Form waitingUpTo(Duration longest) {
      return new Form(endpoint, fields, purpose, emptyAnswerIsNormal, longest);
    }

    /**
     * Returns the request body.
     *
     * @return the percent-encoded {@code name=value} pairs joined by {@code &}
     */
    public String encodedBody() {
      return fields.entrySet().stream()
          .map(
              entry ->
                  FormEncoding.encode(entry.getKey().wireName())
                      + '='
                      + FormEncoding.encode(entry.getValue()))
          .collect(Collectors.joining("&"));
    }

    @Override
    public String describe() {
      return purpose.isEmpty() ? "POST " + endpoint.fileName() : purpose;
    }
  }

  /**
   * A POST that carries a file as its raw body, as {@code application/octet-stream} with a known
   * length. The file is streamed from disk, never held in memory.
   *
   * @param endpoint the CGI program
   * @param query the query parameters in the order they are sent
   * @param file the file to send
   * @param length the number of bytes in the file, sent as the content length
   * @param phase what progress events of the upload are called
   * @param item what progress events of the upload name, for example the file's name
   * @param purpose what the request does, in a few words, for log messages; may be empty
   * @param readTimeout how long the device may stay silent while it takes the file and answers
   */
  record Upload(
      Endpoint endpoint,
      SequencedMap<QueryParameter, String> query,
      Path file,
      long length,
      ProgressPhase phase,
      String item,
      String purpose,
      Duration readTimeout)
      implements CgiRequest {

    /** Takes an immutable copy of the query. */
    public Upload {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(item, "item");
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(readTimeout, "readTimeout");
      if (length < 0) {
        throw new IllegalArgumentException("a file cannot have a negative length");
      }
      query = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(query));
    }

    /**
     * Starts the upload of a file.
     *
     * @param endpoint the CGI program
     * @param file the file to send
     * @param length the number of bytes in the file
     * @param phase what progress events of the upload are called
     * @param item what progress events of the upload name
     * @return an upload without query parameters
     */
    public static Upload of(
        Endpoint endpoint, Path file, long length, ProgressPhase phase, String item) {
      return new Upload(
          endpoint, new LinkedHashMap<>(), file, length, phase, item, "", DEFAULT_READ_TIMEOUT);
    }

    /**
     * Adds a query parameter.
     *
     * @param parameter the parameter
     * @param value its value
     * @return the upload with the parameter added
     */
    public Upload with(QueryParameter parameter, WireValue value) {
      var copy = new LinkedHashMap<>(query);
      copy.put(parameter, value.wireName());
      return new Upload(endpoint, copy, file, length, phase, item, purpose, readTimeout);
    }

    /**
     * Sets what the request does, for log messages.
     *
     * @param what the purpose, in a few words
     * @return the upload with that purpose
     */
    public Upload describedAs(String what) {
      return new Upload(endpoint, query, file, length, phase, item, what, readTimeout);
    }

    /**
     * Sets how long the device may stay silent while it takes the file and answers.
     *
     * @param longest the longest silence that is waited for
     * @return the upload with that limit
     */
    public Upload waitingUpTo(Duration longest) {
      return new Upload(endpoint, query, file, length, phase, item, purpose, longest);
    }

    /**
     * Returns the path and the query string of the request.
     *
     * @return for example {@code /cgi-bin/update.cgi?type=bar}
     */
    public String pathAndQuery() {
      if (query.isEmpty()) {
        return endpoint.path();
      }
      return endpoint.path()
          + '?'
          + query.entrySet().stream()
              .map(
                  entry ->
                      FormEncoding.encode(entry.getKey().wireName())
                          + '='
                          + FormEncoding.encode(entry.getValue()))
              .collect(Collectors.joining("&"));
    }

    @Override
    public boolean emptyAnswerIsNormal() {
      return false;
    }

    @Override
    public String describe() {
      return purpose.isEmpty() ? "POST a file to " + endpoint.fileName() : purpose;
    }
  }
}
