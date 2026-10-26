package com.jbraddockm.core.tunnel;

import java.io.IOException;
import java.io.Serial;

/**
 * A broker request that was refused with {@code {ok:false, error:{code, message}}}. Extends {@link
 * IOException} so a consumer can catch all broker failures — transport and refusal alike — in one
 * place, then inspect {@link #code} when it needs the reason (e.g. {@code unknown_guid}).
 */
public final class BrokerClientException extends IOException {

  @Serial private static final long serialVersionUID = 1L;

  private final String code;

  /**
   * @param code the broker's machine token for the failure
   * @param message the broker's human-readable message
   */
  public BrokerClientException(String code, String message) {
    super(code + ": " + message);
    this.code = code;
  }

  /** The broker's short machine token for this failure. */
  public String code() {
    return code;
  }
}
