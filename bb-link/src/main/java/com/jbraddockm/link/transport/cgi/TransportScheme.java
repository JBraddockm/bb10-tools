package com.jbraddockm.link.transport.cgi;

/** The URL scheme used to reach the device. */
public enum TransportScheme {

  /** TLS with certificate checks disabled, which is what devices need. The default. */
  HTTPS("https"),

  /** Plain HTTP. Only a test double of a device is served this way. */
  HTTP("http");

  private final String urlScheme;

  TransportScheme(String urlScheme) {
    this.urlScheme = urlScheme;
  }

  /**
   * Returns the scheme as it appears in a URL.
   *
   * @return {@code https} or {@code http}
   */
  public String urlScheme() {
    return urlScheme;
  }
}
