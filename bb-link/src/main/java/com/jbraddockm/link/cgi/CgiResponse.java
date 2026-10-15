package com.jbraddockm.link.cgi;

/**
 * What the device answered to one request.
 *
 * @param statusCode the HTTP status code
 * @param contentType the {@code Content-Type} header, or {@code null} if absent
 * @param body the body decoded as UTF-8
 * @param setCookie the value of the first {@code Set-Cookie} header, or {@code null} if absent
 */
public record CgiResponse(int statusCode, String contentType, String body, String setCookie) {}
