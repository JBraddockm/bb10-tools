package com.jbraddockm.device;

import com.jbraddockm.DeployError;
import com.jbraddockm.ErrorCode;
import java.io.Serial;
import java.util.Map;
import java.util.Objects;

/** Thrown when a command or a device target is built from unacceptable values. */
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
   * @throws NullPointerException if {@code parameter} is {@code null}
   */
  public InvalidArgumentException(String parameter, String reason) {
    Objects.requireNonNull(
        parameter, "parameter"); // validated before the superclass is initialised
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
   * Describes this exception as an {@link ErrorCode#INVALID_ARGUMENT} error that names the
   * parameter.
   *
   * @return the error
   */
  public DeployError toDeployError() {
    return new DeployError(ErrorCode.INVALID_ARGUMENT, getMessage(), Map.of("param", parameter));
  }
}
