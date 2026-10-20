package com.jbraddockm.core.error;

import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/** Thrown when a request parameter or a value derived from it is unacceptable. */
public final class InvalidArgumentException extends IllegalArgumentException {

  @Serial private static final long serialVersionUID = 1L;

  /** The name of the offending parameter. */
  private final String parameter;

  /**
   * Creates the exception. The message reads {@code 'parameter' reason}.
   *
   * @param parameter the name of the offending parameter, which is also its name in the wire
   *     protocol
   * @param reason what is wrong with it, for example {@code is required}
   * @throws NullPointerException if {@code parameter} or {@code reason} is {@code null}
   */
  public InvalidArgumentException(String parameter, String reason) {
    Objects.requireNonNull(parameter, "parameter");
    Objects.requireNonNull(reason, "reason");
    super("'" + parameter + "' " + reason);
    this.parameter = parameter;
  }

  /**
   * Returns the name of the offending parameter.
   *
   * @return the parameter name
   */
  public String parameter() {
    return parameter;
  }

  /**
   * Describes this exception as a {@link CoreErrorCode#INVALID_ARGUMENT} error that names the
   * parameter.
   *
   * @return the error
   */
  public ServiceError toServiceError() {
    return new ServiceError(
        CoreErrorCode.INVALID_ARGUMENT, getMessage(), Map.of("param", parameter));
  }
}
