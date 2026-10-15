package com.jbraddockm.link.internal;

import java.util.regex.Pattern;

/**
 * Removes what must never leave the process from text that came from, or goes to, the device.
 *
 * <p>Three levels exist: {@link #secrets} for credentials that are removed from everything, {@link
 * #identifiers} for traces that are shared with others, and {@link #forDisplay} for device messages
 * shown to a user.
 */
public final class Scrub {

  private static final Pattern SESSION_SECRETS =
      Pattern.compile("(dtmauth=|loginsession=|challenge_data=)[^;\\s\"&<]+");
  private static final Pattern COOKIE_ECHO = Pattern.compile("HTTP_COOKIE=[^<\\r\\n]*");

  /** The login answer hands out a password for the device's file share, as an attribute... */
  private static final Pattern SHARE_PASSWORD_ATTRIBUTE =
      Pattern.compile("(?i)(\\bPassword\\s*=\\s*['\"])[^'\"]*(['\"])");

  /** ...and as an element. */
  private static final Pattern SHARE_PASSWORD_ELEMENT =
      Pattern.compile("(?i)(<(Pwd)(?:\\s[^>]*)?>)[^<]+(</\\2>)");

  /**
   * Names are matched ignoring case: the device writes {@code Imei}, other sources {@code IMEI}.
   */
  private static final Pattern IDENTIFIER_ELEMENTS =
      Pattern.compile(
          "(?i)(<(PIN|BoardSerialNumber|Bbid|Ecoid|Fingerprint|Imei|Meid|PhoneNumber|Ecid|Scid|BSN|ProcessorId)"
              + "(?:\\s[^>]*)?>)[^<]+(</\\2>)");

  private Scrub() {}

  /**
   * Masks the login cookies, the login answer, the password of the device's file share and any echo
   * of the request's cookie header.
   *
   * @param text any text
   * @return the text with secrets replaced by {@code ***}
   */
  public static String secrets(String text) {
    var withoutSession = SESSION_SECRETS.matcher(text).replaceAll("$1***");
    var withoutCookieEcho = COOKIE_ECHO.matcher(withoutSession).replaceAll("HTTP_COOKIE=***");
    var withoutAttribute =
        SHARE_PASSWORD_ATTRIBUTE.matcher(withoutCookieEcho).replaceAll("$1***$2");
    return SHARE_PASSWORD_ELEMENT.matcher(withoutAttribute).replaceAll("$1***$3");
  }

  /**
   * Masks secrets and the values that identify one device or its owner (PIN, serial number, IMEI,
   * chip identifiers, BlackBerry ID, package fingerprints), so a trace can be shared. An element
   * that is empty stays empty, which keeps "no value" visible.
   *
   * @param text any text
   * @return the text with identifying values replaced by {@code ***}
   */
  public static String identifiers(String text) {
    return IDENTIFIER_ELEMENTS.matcher(secrets(text)).replaceAll("$1***$3");
  }

  /**
   * Removes an echo of the request's cookie header from a message the device wrote, because it
   * contains the login cookie.
   *
   * @param text a device message
   * @return the message without the cookie echo, stripped of surrounding whitespace
   */
  public static String forDisplay(String text) {
    return COOKIE_ECHO.matcher(text).replaceAll("").strip();
  }
}
