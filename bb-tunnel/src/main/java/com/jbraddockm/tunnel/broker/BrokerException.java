package com.jbraddockm.tunnel.broker;

import java.io.Serial;

/**
 * A broker request that cannot be served for a reason the client should see: the {@link #code} is a
 * short machine token ({@code unknown_guid}, {@code unknown_host}, …) and the message is
 * human-readable. {@link BrokerServer} turns it into the request's {@code {ok:false, error:{code,
 * message}}} reply.
 */
final class BrokerException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * @param code a short, stable machine token for the failure
   * @param message a human-readable explanation
   */
  BrokerException(String code, String message) {
    super(message);
    this.code = code;
  }

  /** The short machine token for this failure. */
  String code() {
    return code;
  }
}
