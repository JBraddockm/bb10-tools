package com.jbraddockm.link.cgi;

import java.nio.charset.StandardCharsets;

/**
 * Percent-encoding for {@code application/x-www-form-urlencoded} bodies. A space becomes {@code
 * %20}, as the device's own clients send it, not {@code +}.
 */
final class FormEncoding {

  private static final String HEX = "0123456789ABCDEF";

  private FormEncoding() {}

  /**
   * Percent-encodes a text.
   *
   * @param text the text
   * @return the text with everything but letters, digits, {@code - _ . ~} written as {@code %XX} of
   *     its UTF-8 bytes
   */
  public static String encode(String text) {
    var builder = new StringBuilder();
    for (byte value : text.getBytes(StandardCharsets.UTF_8)) {
      var unsigned = value & 0xFF;
      if (isUnreserved(unsigned)) {
        builder.append((char) unsigned);
      } else {
        builder.append('%').append(HEX.charAt(unsigned >> 4)).append(HEX.charAt(unsigned & 0xF));
      }
    }
    return builder.toString();
  }

  private static boolean isUnreserved(int value) {
    return (value >= 'A' && value <= 'Z')
        || (value >= 'a' && value <= 'z')
        || (value >= '0' && value <= '9')
        || value == '-'
        || value == '_'
        || value == '.'
        || value == '~';
  }
}
