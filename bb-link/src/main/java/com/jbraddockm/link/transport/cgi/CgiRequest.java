package com.jbraddockm.link.transport.cgi;

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

  /**
   * A POST that carries a text as its raw body, with query parameters: what the client's request
   * for a certificate to {@code login.cgi} is said to be **[RESEARCH]**.
   *
   * @param endpoint the CGI program
   * @param query the query parameters in the order they are sent
   * @param text the body
   * @param contentType the content type that the body is sent as
   * @param purpose what the request does, in a few words, for log messages; may be empty
   * @param readTimeout how long the device may stay silent
   */
  record Body(
      Endpoint endpoint,
      SequencedMap<QueryParameter, String> query,
      String text,
      String contentType,
      String purpose,
      Duration readTimeout)
      implements CgiRequest {

    /**
     * Checks the components.
     *
     * @throws NullPointerException if a component is missing
     * @throws IllegalArgumentException if the text or the content type is blank
     */
    public Body {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(text, "text");
      Objects.requireNonNull(contentType, "contentType");
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(readTimeout, "readTimeout");
      if (text.isBlank() || contentType.isBlank()) {
        throw new IllegalArgumentException("a body and its content type are not blank");
      }
      query = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(query));
    }

    /**
     * Starts a request without query parameters.
     *
     * @param endpoint the CGI program
     * @param text the body
     * @param contentType the content type of the body
     * @return the request
     */
    public static Body of(Endpoint endpoint, String text, String contentType) {
      return new Body(endpoint, new LinkedHashMap<>(), text, contentType, "", DEFAULT_READ_TIMEOUT);
    }

    /**
     * Adds a query parameter after the ones that are there.
     *
     * @param parameter the parameter
     * @param value its value
     * @return the request with the parameter
     */
    public Body with(QueryParameter parameter, String value) {
      var copy = new LinkedHashMap<>(query);
      copy.put(parameter, value);
      return new Body(endpoint, copy, text, contentType, purpose, readTimeout);
    }

    /**
     * Says what the request does, for log messages.
     *
     * @param what a few words
     * @return the request with that purpose
     */
    public Body describedAs(String what) {
      return new Body(endpoint, query, text, contentType, what, readTimeout);
    }

    /**
     * Returns the path and the query string of the request.
     *
     * @return for example {@code /cgi-bin/login.cgi?request_version=3&cgiauth=1}
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
      return purpose.isEmpty() ? "POST a text to " + endpoint.fileName() : purpose;
    }
  }

  /**
   * A form post whose answer is a data stream, written straight to a file rather than held in
   * memory: the device's answer to a backup {@code type=<category>} step is the archive itself,
   * tens to hundreds of megabytes of {@code application/octet-stream}. When the device answers with
   * XML instead — an error envelope — the transport keeps that in {@link CgiResponse#body()} as
   * usual, so the conversation can read the error.
   *
   * @param endpoint the CGI program
   * @param fields the form fields in the order they are sent
   * @param destination the file the data answer is written to
   * @param expectedLength the number of bytes the answer is expected to have, for progress; may be
   *     {@code 0} when it is not known yet
   * @param phase what progress events of the download are called
   * @param item what progress events of the download name, for example the category
   * @param purpose what the request does, in a few words, for log messages; may be empty
   * @param readTimeout how long the device may stay silent while it streams the answer
   */
  record Download(
      Endpoint endpoint,
      SequencedMap<FormField, String> fields,
      Path destination,
      long expectedLength,
      ProgressPhase phase,
      String item,
      String purpose,
      Duration readTimeout)
      implements CgiRequest {

    /** Takes an immutable copy of the fields. */
    public Download {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(destination, "destination");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(item, "item");
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(readTimeout, "readTimeout");
      if (expectedLength < 0) {
        throw new IllegalArgumentException("a download cannot have a negative length");
      }
      fields = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(fields));
    }

    /**
     * Starts a download to a file.
     *
     * @param endpoint the CGI program
     * @param destination the file the answer is written to
     * @param expectedLength the bytes the answer is expected to have, for progress; {@code 0} if
     *     unknown
     * @param phase what progress events of the download are called
     * @param item what progress events of the download name
     * @return a download without fields
     */
    public static Download to(
        Endpoint endpoint,
        Path destination,
        long expectedLength,
        ProgressPhase phase,
        String item) {
      return new Download(
          endpoint,
          new LinkedHashMap<>(),
          destination,
          expectedLength,
          phase,
          item,
          "",
          DEFAULT_READ_TIMEOUT);
    }

    /**
     * Adds a field whose value is one of the device's fixed words.
     *
     * @param field the field
     * @param value the value
     * @return a new download with the field appended
     */
    public Download with(FormField field, WireValue value) {
      return withText(field, value.wireName());
    }

    /**
     * Adds a field with free text as value.
     *
     * @param field the field
     * @param text the value
     * @return a new download with the field appended
     */
    public Download withText(FormField field, String text) {
      var copy = new LinkedHashMap<>(fields);
      copy.put(field, text);
      return new Download(
          endpoint, copy, destination, expectedLength, phase, item, purpose, readTimeout);
    }

    /**
     * Says what the request does, for log messages.
     *
     * @param what a few words in the present participle, for example {@code Downloading the
     *     settings}
     * @return a new download with the purpose set
     */
    public Download describedAs(String what) {
      return new Download(
          endpoint, fields, destination, expectedLength, phase, item, what, readTimeout);
    }

    /**
     * Sets how long the device may stay silent while it streams the answer.
     *
     * @param longest the longest silence that is waited for
     * @return the download with that limit
     */
    public Download waitingUpTo(Duration longest) {
      return new Download(
          endpoint, fields, destination, expectedLength, phase, item, purpose, longest);
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
    public boolean emptyAnswerIsNormal() {
      // A streamed answer leaves the body empty by design; the conversation reads the file, not the
      // body, so an empty body here is never a sign of a lost session.
      return true;
    }

    @Override
    public String describe() {
      return purpose.isEmpty() ? "Downloading from " + endpoint.fileName() : purpose;
    }
  }

  /**
   * A {@code multipart/form-data} POST that carries one file part, with the rest of the request in
   * the query string: how a contact photo is put. The CGI reads {@code command}, {@code service},
   * {@code workstation_id} and {@code data} from the query — a multipart body is not parsed for
   * them — and takes the file from the one part, named by {@link #fileField}. The file is read into
   * memory; contact photos are small, and the device refuses anything over its size limit with
   * status 1004 rather than this program guessing a limit.
   *
   * @param endpoint the CGI program
   * @param query the query parameters in the order they are sent
   * @param file the file to send as the one part
   * @param fileField the name of the multipart field the file is sent under
   * @param fileContentType the content type the part is sent as, for example {@code image/jpeg}
   * @param phase what progress events of the upload are called
   * @param item what progress events name, for example the file's name
   * @param purpose what the request does, in a few words, for log messages; may be empty
   * @param readTimeout how long the device may stay silent while it takes the file and answers
   */
  record Multipart(
      Endpoint endpoint,
      SequencedMap<QueryParameter, String> query,
      Path file,
      String fileField,
      String fileContentType,
      ProgressPhase phase,
      String item,
      String purpose,
      Duration readTimeout)
      implements CgiRequest {

    /** Takes an immutable copy of the query. */
    public Multipart {
      Objects.requireNonNull(endpoint, "endpoint");
      Objects.requireNonNull(file, "file");
      Objects.requireNonNull(fileField, "fileField");
      Objects.requireNonNull(fileContentType, "fileContentType");
      Objects.requireNonNull(phase, "phase");
      Objects.requireNonNull(item, "item");
      Objects.requireNonNull(purpose, "purpose");
      Objects.requireNonNull(readTimeout, "readTimeout");
      query = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(query));
    }

    /**
     * Starts a multipart upload of one file.
     *
     * @param endpoint the CGI program
     * @param file the file to send
     * @param fileField the multipart field name the file is sent under
     * @param fileContentType the content type of the part
     * @param phase what progress events of the upload are called
     * @param item what progress events name
     * @return a multipart request without query parameters
     */
    public static Multipart of(
        Endpoint endpoint,
        Path file,
        String fileField,
        String fileContentType,
        ProgressPhase phase,
        String item) {
      return new Multipart(
          endpoint,
          new LinkedHashMap<>(),
          file,
          fileField,
          fileContentType,
          phase,
          item,
          "",
          DEFAULT_READ_TIMEOUT);
    }

    /**
     * Adds a query parameter whose value is one of the device's fixed words.
     *
     * @param parameter the parameter
     * @param value the value
     * @return the request with the parameter appended
     */
    public Multipart with(QueryParameter parameter, WireValue value) {
      return withText(parameter, value.wireName());
    }

    /**
     * Adds a query parameter with free text as value.
     *
     * @param parameter the parameter
     * @param text the value
     * @return the request with the parameter appended
     */
    public Multipart withText(QueryParameter parameter, String text) {
      var copy = new LinkedHashMap<>(query);
      copy.put(parameter, text);
      return new Multipart(
          endpoint, copy, file, fileField, fileContentType, phase, item, purpose, readTimeout);
    }

    /**
     * Says what the request does, for log messages.
     *
     * @param what a few words in the present participle
     * @return the request with the purpose set
     */
    public Multipart describedAs(String what) {
      return new Multipart(
          endpoint, query, file, fileField, fileContentType, phase, item, what, readTimeout);
    }

    /**
     * Returns the path and the query string of the request.
     *
     * @return for example {@code /cgi-bin/pimsync.cgi?command=put&service=photos}
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
