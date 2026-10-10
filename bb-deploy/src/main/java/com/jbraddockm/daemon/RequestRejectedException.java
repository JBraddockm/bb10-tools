package com.jbraddockm.daemon;

import com.jbraddockm.DeployError;
import java.io.Serial;

/** Thrown while handling a request that must be answered with a particular error. */
final class RequestRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient DeployError error;

  RequestRejectedException(DeployError error) {
    super(error.message(), null, false, false); // control flow only: no stack trace needed
    this.error = error;
  }

  DeployError error() {
    return error;
  }
}
