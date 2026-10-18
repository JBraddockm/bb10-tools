package com.jbraddockm.link.pairing;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Optional;

/** Where the names in a certificate come from: the PIN, the user and the computer. */
final class ClientNames {

  private static final String UNKNOWN = "unknown";

  private ClientNames() {}

  /**
   * Makes a name safe to put in a common name: letters, digits, dots, dashes and underscores stay,
   * everything else becomes a dash.
   *
   * @param name the name as it is
   * @return the safe name; {@code unknown} if nothing is left
   */
  static String sanitise(String name) {
    var safe = name.strip().replaceAll("[^A-Za-z0-9._-]", "-");
    return safe.isBlank() ? UNKNOWN : safe;
  }

  /**
   * Writes a PIN as {@code %X} does: capitals, without leading zeros.
   *
   * @param pin the PIN in hexadecimal digits, with or without {@code 0x}
   * @return the digits
   * @throws NumberFormatException if it is not hexadecimal
   */
  static String pinHex(String pin) {
    var digits = pin.strip().toUpperCase(Locale.ROOT);
    return Long.toHexString(Long.parseUnsignedLong(digits.replaceFirst("^0X", ""), 16))
        .toUpperCase(Locale.ROOT);
  }

  static String localWorkstation() {
    var fromEnvironment =
        Optional.ofNullable(System.getenv("COMPUTERNAME"))
            .or(() -> Optional.ofNullable(System.getenv("HOSTNAME")));
    var name =
        fromEnvironment.orElseGet(
            () -> {
              try {
                return InetAddress.getLocalHost().getHostName();
              } catch (UnknownHostException _) {
                return UNKNOWN;
              }
            });
    var dot = name.indexOf('.');
    return sanitise(dot > 0 ? name.substring(0, dot) : name);
  }
}
