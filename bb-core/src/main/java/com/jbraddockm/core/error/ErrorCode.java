package com.jbraddockm.core.error;

/**
 * A stable, machine-readable identifier for something that can go wrong. Clients branch on the
 * code, never on the accompanying message text.
 *
 * <p>Implementations are enums, so every tool can add its own codes without touching the shared
 * ones. The wire names are part of each tool's protocol and must not be renamed.
 */
public interface ErrorCode {

  /**
   * Returns the identifier as it appears in the wire protocol.
   *
   * @return the wire name, for example {@code AUTH_FAILED}
   */
  String wireName();
}
