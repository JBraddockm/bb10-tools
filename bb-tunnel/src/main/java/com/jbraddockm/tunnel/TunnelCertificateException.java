package com.jbraddockm.tunnel;

/**
 * The client identity for a tunnel could not be loaded: the certificate or key file is missing,
 * unreadable, or not valid PEM. Carries a short machine {@link #code} so the broker can report a
 * clear, structured reason to its consumers instead of a generic failure (see {@code
 * analysis/TUNNEL-BROKER-DAEMON-PLAN.md} §cert loading).
 *
 * <p>Codes: {@code cert_not_found}, {@code cert_unreadable}, {@code cert_malformed}, {@code
 * key_not_found}, {@code key_unreadable}, {@code key_malformed}, {@code identity_missing} (no
 * identity was supplied at all).
 */
public final class TunnelCertificateException extends Exception {

  private final String code;

  TunnelCertificateException(String code, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
  }

  TunnelCertificateException(String code, String message) {
    this(code, message, null);
  }

  /** The short machine token for this failure (e.g. {@code cert_not_found}). */
  public String code() {
    return code;
  }
}
