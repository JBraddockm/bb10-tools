package com.jbraddockm.link.session;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Where the client certificate and its private key are, as PEM files.
 *
 * <p>The files are read every time a connection to the device is set up, so a certificate that is
 * replaced on disk is used from then on. The key is held in memory only while a session exists and
 * is never logged.
 *
 * @param certificateFile the certificate, and optionally the chain behind it, leaf first
 * @param keyFile the private key of the certificate, unencrypted, as {@code PRIVATE KEY} (PKCS #8)
 *     or {@code RSA PRIVATE KEY} (PKCS #1)
 */
public record ClientCertificate(Path certificateFile, Path keyFile) {

  /**
   * Validates the location.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public ClientCertificate {
    Objects.requireNonNull(certificateFile, "certificateFile");
    Objects.requireNonNull(keyFile, "keyFile");
  }
}
