package com.jbraddockm.link.cgi;

import java.util.Locale;

/**
 * The state that a device's answer says its operation is in, from the {@code Status} element.
 *
 * <p>The words are the ones of the clients' own parsing of the answers, compared without regard to
 * case. Each CGI program has its own wrapper element around {@code Status}; this enum is only the
 * word.
 */
public enum ResponseStatus {

  /** The operation succeeded. */
  SUCCESS,

  /** The device is ready for the next step. */
  READY,

  /** The operation has not finished. */
  IN_PROGRESS,

  /** The device refused the login. */
  DENIED,

  /** The device asks for the password. */
  PASSWORD_CHALLENGE,

  /** The operation failed; the answer says why. */
  ERROR,

  /** A word that is not one of these, or none. */
  UNKNOWN;

  /**
   * Reads the text of a {@code Status} element.
   *
   * @param text the text; may be {@code null}
   * @return the status; {@link #UNKNOWN} if the text is none of the words. {@code 0}, which one
   *     research source gives for success, is taken for success.
   */
  public static ResponseStatus of(String text) {
    var word = text == null ? "" : text.strip().toLowerCase(Locale.ROOT);
    return switch (word) {
      case "success", "0" -> SUCCESS;
      case "ready" -> READY;
      case "inprogress", "in progress" -> IN_PROGRESS;
      case "denied" -> DENIED;
      case "passwdchallenge", "passwordchallenge" -> PASSWORD_CHALLENGE;
      case "error" -> ERROR;
      default -> UNKNOWN;
    };
  }
}
