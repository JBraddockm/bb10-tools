package com.jbraddockm.link.session;

import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import java.util.Optional;

/**
 * Recognises that the device has forgotten the login.
 *
 * <p>A request with a made-up cookie to {@code dynamicProperties.cgi} is answered with status 200
 * and an empty body **[verified]**. {@code pimsync.cgi} answers a request without any session with
 * the text in {@link #NOT_LOGGED_IN} **[verified without a cookie, not with an expired one]**.
 * Further signs are still guesses and stay as a precaution: an HTTP authorisation failure, or the
 * authentication elements that the login interface uses.
 */
final class StaleSession {

  private static final int OK = 200;

  /** What pimsync.cgi says to a client without a session; the text is not valid JSON. */
  private static final String NOT_LOGGED_IN = "DTM NOT LOGGED IN";

  private static final int UNAUTHORIZED = 401;
  private static final int FORBIDDEN = 403;

  private StaleSession() {}

  static boolean indicatedBy(
      CgiRequest request, CgiResponse response, Optional<ResponseEnvelope> envelope) {
    if (response.statusCode() == UNAUTHORIZED || response.statusCode() == FORBIDDEN) {
      return true;
    }
    if (response.statusCode() == OK
        && response.body().isBlank()
        && !request.emptyAnswerIsNormal()) {
      return true;
    }
    if (response.body().contains(NOT_LOGGED_IN)) {
      return true;
    }
    return envelope
        .flatMap(ResponseEnvelope::element)
        .map(
            element -> element == ResponseElement.AUTH_CHALLENGE || element == ResponseElement.AUTH)
        .orElse(false);
  }
}
