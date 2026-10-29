package com.jbraddockm.link.transport.cgi;

import java.nio.file.Path;

/**
 * What the device answered to one request.
 *
 * <p>Most answers are small and are kept in {@link #body} as text. A data answer, such as a backup
 * archive, is streamed to a file instead and named by {@link #downloaded}; its {@link #body} is then
 * empty, so that megabytes of binary never pass through a {@code String}.
 *
 * @param statusCode the HTTP status code
 * @param contentType the {@code Content-Type} header, or {@code null} if absent
 * @param body the body decoded as UTF-8, or empty when the answer was streamed to a file
 * @param setCookie the value of the first {@code Set-Cookie} header, or {@code null} if absent
 * @param downloaded the file the answer was streamed to, or {@code null} when the answer is in
 *     {@link #body}
 */
public record CgiResponse(
    int statusCode, String contentType, String body, String setCookie, Downloaded downloaded) {

  /** A data answer that was streamed to a file rather than kept in memory. */
  public record Downloaded(Path file, long bytes) {}

  /**
   * An answer kept in memory, as every answer but a streamed download is.
   *
   * @param statusCode the HTTP status code
   * @param contentType the {@code Content-Type} header, or {@code null} if absent
   * @param body the body decoded as UTF-8
   * @param setCookie the value of the first {@code Set-Cookie} header, or {@code null} if absent
   */
  public CgiResponse(int statusCode, String contentType, String body, String setCookie) {
    this(statusCode, contentType, body, setCookie, null);
  }
}
