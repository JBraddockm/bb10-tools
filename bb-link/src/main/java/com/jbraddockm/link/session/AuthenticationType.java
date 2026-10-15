package com.jbraddockm.link.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Arrays;
import java.util.stream.Collectors;

/** The ways a client can prove who it is to the device. */
public enum AuthenticationType {

  /** The device has no password. The client logs in without one. */
  NONE("none"),

  /** The client logs in with the device password. */
  PASSWORD("password"),

  /** The client offers a client certificate and does not log in. */
  CERTIFICATE("certificate"),

  /** The client offers a client certificate and logs in with the device password as well. */
  CERTIFICATE_AND_PASSWORD("certificateAndPassword");

  private final String wireName;

  AuthenticationType(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the name used in the wire protocol.
   *
   * @return for example {@code certificateAndPassword}
   */
  public String wireName() {
    return wireName;
  }

  /**
   * Recognises a type by its wire name.
   *
   * @param name the name as sent by a client
   * @return the type
   * @throws InvalidArgumentException naming {@code authentication.type} and the accepted values
   */
  public static AuthenticationType of(String name) {
    return Arrays.stream(values())
        .filter(type -> type.wireName.equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidArgumentException(
                    "authentication.type",
                    "must be one of: "
                        + Arrays.stream(values())
                            .map(AuthenticationType::wireName)
                            .collect(Collectors.joining(", "))));
  }
}
