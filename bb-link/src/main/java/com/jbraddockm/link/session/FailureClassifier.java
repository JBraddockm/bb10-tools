package com.jbraddockm.link.session;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLException;
import org.apache.http.NoHttpResponseException;
import org.apache.http.conn.ConnectTimeoutException;

/** Maps failures to errors and decides what may be retried. */
final class FailureClassifier {

  private FailureClassifier() {}

  static ServiceError classify(Throwable failure) {
    return classify(failure, false);
  }

  /**
   * Maps a failure to an error.
   *
   * @param failure what went wrong
   * @param certificateOffered whether a client certificate was offered to the device; a failed
   *     handshake is then explained with that in mind
   */
  static ServiceError classify(Throwable failure, boolean certificateOffered) {
    return classify(failure, certificateOffered, ConnectionPath.OTHER);
  }

  /**
   * Classifies a failure, naming the connection path where it helps.
   *
   * @param failure what went wrong
   * @param certificateOffered whether a client certificate was offered
   * @param path the path the session was reached over
   * @return the error
   */
  static ServiceError classify(Throwable failure, boolean certificateOffered, ConnectionPath path) {
    return switch (failure) {
      case InvalidArgumentException invalid -> invalid.toServiceError();
      case PasswordRejectedException rejected when rejected.isLocked() ->
          new ServiceError(
              CoreErrorCode.AUTH_LOCKED,
              describe(rejected),
              Json.object("failedAttempts", rejected.failedAttempts()));
      case PasswordRejectedException rejected ->
          new ServiceError(
              CoreErrorCode.AUTH_FAILED,
              describe(rejected),
              Json.object(
                  "failedAttempts", rejected.failedAttempts(),
                  "retriesRemaining", rejected.remainingAttempts(),
                  "maxAttempts", rejected.failedAttempts() + rejected.remainingAttempts()));
      case InvalidPasswordException invalid ->
          new ServiceError(CoreErrorCode.AUTH_FAILED, describe(invalid));
      case PasswordRequiredException required ->
          new ServiceError(LinkErrorCode.PASSWORD_REQUIRED, describe(required));
      case ClockOutOfSyncException clock ->
          new ServiceError(CoreErrorCode.CLOCK_OUT_OF_SYNC, describe(clock));
      case UnusableLoginResponseException unusable ->
          new ServiceError(
              CoreErrorCode.DEVICE_ERROR,
              describe(unusable),
              Json.object("extra", unusable.extra()));
      case ConnectException _ -> connection(CoreErrorCode.CONNECTION_FAILED, "refused", failure);
      case ConnectTimeoutException _ ->
          connection(CoreErrorCode.CONNECTION_FAILED, "timeout", failure);
      case UnknownHostException _ ->
          connection(CoreErrorCode.CONNECTION_FAILED, "unknown_host", failure);
      case SSLException _ when certificateOffered ->
          new ServiceError(
              CoreErrorCode.CONNECTION_FAILED,
              "The TLS handshake failed while a client certificate was offered; the device may have"
                  + " refused it (original message: "
                  + describe(failure)
                  + ")",
              Json.object("cause", "tls", "certificate", true));
      case SSLException _ when path == ConnectionPath.TUNNEL ->
          new ServiceError(
              CoreErrorCode.CONNECTION_FAILED,
              "The TLS handshake failed; a tunnel connection needs the paired client certificate"
                  + " (original message: "
                  + describe(failure)
                  + ")",
              Json.object("cause", "tls"));
      case SSLException _ -> connection(CoreErrorCode.CONNECTION_FAILED, "tls", failure);
      case SocketTimeoutException _ ->
          connection(CoreErrorCode.CONNECTION_LOST, "timeout", failure);
      case SocketException _, NoHttpResponseException _ ->
          connection(CoreErrorCode.CONNECTION_LOST, "reset", failure);
      case MalformedURLException _ ->
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, describe(failure));
      case IOException _ -> new ServiceError(CoreErrorCode.IO_ERROR, describe(failure));
      case IllegalArgumentException _ ->
          new ServiceError(CoreErrorCode.INVALID_ARGUMENT, describe(failure));
      default -> ServiceError.internal(failure);
    };
  }

  /**
   * Tells whether a failure looks like a dead pooled connection, which is worth one retry, as
   * opposed to a device that cannot be reached at all.
   */
  static boolean isStaleConnection(Throwable failure) {
    return switch (failure) {
      case ConnectTimeoutException _, ConnectException _, UnknownHostException _ -> false;
      case SocketException _, NoHttpResponseException _ -> true;
      default -> false;
    };
  }

  /**
   * Tells whether the connection stays usable after a failure. A bad argument does not damage it;
   * anything else may have left a half-finished exchange behind.
   */
  static boolean leavesSessionUsable(Throwable failure) {
    return failure instanceof IllegalArgumentException;
  }

  private static ServiceError connection(ErrorCode code, String cause, Throwable failure) {
    return new ServiceError(code, describe(failure), Json.object("cause", cause));
  }

  private static String describe(Throwable failure) {
    var message = failure.getLocalizedMessage();
    return message != null && !message.isBlank()
        ? message.strip()
        : failure.getClass().getSimpleName();
  }
}
