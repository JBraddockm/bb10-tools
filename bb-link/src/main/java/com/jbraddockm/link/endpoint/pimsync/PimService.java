package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * The kinds of items that can be read. Photos are not among them: the device returns them as image
 * bytes.
 */
public enum PimService implements WireValue {

  /** The address book. */
  CONTACTS("contacts"),

  /** Calendar events. */
  EVENTS("events"),

  /** The calendars themselves. */
  CALENDARS("calendars");

  private final String wireName;

  PimService(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  /**
   * Recognises a service by its wire name.
   *
   * @param name the name as sent by a client
   * @return the service
   * @throws InvalidArgumentException naming the parameter {@code service} and the accepted values
   */
  public static PimService of(String name) {
    return Arrays.stream(values())
        .filter(service -> service.wireName.equals(name))
        .findFirst()
        .orElseThrow(
            () ->
                new InvalidArgumentException(
                    "service",
                    "must be one of: "
                        + Arrays.stream(values())
                            .map(PimService::wireName)
                            .collect(Collectors.joining(", "))));
  }
}
