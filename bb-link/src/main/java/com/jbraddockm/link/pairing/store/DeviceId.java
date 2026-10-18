package com.jbraddockm.link.pairing.store;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The identifier that a computer gives itself when it pairs with a device. The one that BlackBerry
 * Link uses is 34 hexadecimal digits in capitals: {@code 01} and 16 random bytes **[verified]**.
 */
public final class DeviceId {

  private static final Pattern VALID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
  private static final int RANDOM_BYTES = 16;
  private static final String PREFIX = "01";

  private DeviceId() {}

  /**
   * Makes a new identifier.
   *
   * @param random where the bytes come from
   * @return {@code 01} and 32 hexadecimal digits in capitals
   */
  public static String generate(SecureRandom random) {
    var bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    return PREFIX + HexFormat.of().withUpperCase().formatHex(bytes);
  }

  /**
   * Checks an identifier and writes it in capitals, as the device keeps it.
   *
   * @param text the identifier
   * @return the identifier in capitals
   * @throws IllegalArgumentException if it is not 1 to 64 letters, digits, dashes or underscores
   */
  public static String normalise(String text) {
    var stripped = text.strip();
    if (!VALID.matcher(stripped).matches()) {
      throw new IllegalArgumentException(
          "is not an identifier: 1 to 64 letters, digits, dashes or underscores");
    }
    return stripped.toUpperCase(Locale.ROOT);
  }
}
