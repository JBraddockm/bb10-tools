package com.jbraddockm.link.cgi;

import java.util.Arrays;
import java.util.Optional;

/**
 * The response elements this version of bb-link knows. The device names the kind of answer by the
 * first element inside its envelope.
 */
public enum ResponseElement {

  /** The device properties and installed packages. */
  DYNAMIC_PROPERTIES("DynamicProperties"),

  /** The device PIN. */
  DEVICE_PIN("DevicePIN"),

  /** The device wants the client to authenticate again. */
  AUTH_CHALLENGE("AuthChallenge"),

  /** The device's verdict on an authentication. */
  AUTH("Auth");

  private final String elementName;

  ResponseElement(String elementName) {
    this.elementName = elementName;
  }

  /**
   * Returns the element name as the device writes it.
   *
   * @return the element name
   */
  public String elementName() {
    return elementName;
  }

  /**
   * Recognises an element name.
   *
   * @param name the name of an element in a response
   * @return the matching element, or empty if this version does not know it
   */
  public static Optional<ResponseElement> of(String name) {
    return Arrays.stream(values()).filter(known -> known.elementName.equals(name)).findFirst();
  }
}
