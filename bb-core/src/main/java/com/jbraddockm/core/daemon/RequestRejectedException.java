package com.jbraddockm.core.daemon;

import com.jbraddockm.core.error.ServiceError;
import java.io.Serial;

/** Thrown while handling a request that must be answered with a particular error. */
final class RequestRejectedException extends RuntimeException {

  @Serial private static final long serialVersionUID = 1L;

  private final transient ServiceError error;

  RequestRejectedException(ServiceError error) {
    super(error.message(), null, false, false); // control flow only: no stack trace needed
    this.error = error;
  }

  ServiceError error() {
    return error;
  }
}
