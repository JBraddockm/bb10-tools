package com.jbraddockm.link.transport.session;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.Objects;
import java.util.Optional;

/**
 * How a session proves who it is to the device. Exactly one of the variants applies, and a state
 * that makes no sense, such as having neither a password nor a certificate while expecting a login,
 * cannot be written down.
 *
 * <p>The variants differ in what happens at the start of a session:
 *
 * <ul>
 *   <li>{@link NoPassword}: logs in without a password. If the device asks for one after all, the
 *       session stops without sending an answer, so no failed attempt is counted against the
 *       device's lock.
 *   <li>{@link Password}: logs in with the password.
 *   <li>{@link Certificate}: offers the certificate and logs in without a password: the login
 *       request is sent, and if the device asks for a password after all, the session carries on
 *       with the certificate alone and never answers the question.
 *   <li>{@link CertificateAndPassword}: offers the certificate and logs in.
 * </ul>
 *
 * <p>Nothing here prints a password: the records mask it.
 */
public sealed interface Authentication {

  /** The device has no password; the client logs in without one. */
  record NoPassword() implements Authentication {}

  /**
   * The client logs in with the device password.
   *
   * @param password the password; must not be blank
   */
  record Password(String password) implements Authentication {

    /**
     * Validates the password.
     *
     * @throws InvalidArgumentException if it is missing or blank; a device without a password is
     *     {@link NoPassword}
     */
    public Password {
      if (password == null || password.isBlank()) {
        throw new InvalidArgumentException(
            "authentication.password", "is required and must not be blank");
      }
    }

    /** Never reveals the password. */
    @Override
    public String toString() {
      return "Password[***]";
    }
  }

  /**
   * The client offers a certificate and logs in without a password.
   *
   * @param certificate where the certificate and its key are
   */
  record Certificate(ClientCertificate certificate) implements Authentication {

    /**
     * Validates the variant.
     *
     * @throws NullPointerException if the certificate is {@code null}
     */
    public Certificate {
      Objects.requireNonNull(certificate, "certificate");
    }
  }

  /**
   * The client offers a certificate and logs in with the device password.
   *
   * @param certificate where the certificate and its key are
   * @param password the password; must not be blank
   */
  record CertificateAndPassword(ClientCertificate certificate, String password)
      implements Authentication {

    /**
     * Validates the variant.
     *
     * @throws NullPointerException if the certificate is {@code null}
     * @throws InvalidArgumentException if the password is missing or blank
     */
    public CertificateAndPassword {
      Objects.requireNonNull(certificate, "certificate");
      if (password == null || password.isBlank()) {
        throw new InvalidArgumentException(
            "authentication.password", "is required and must not be blank");
      }
    }

    /** Never reveals the password. */
    @Override
    public String toString() {
      return "CertificateAndPassword[certificate=" + certificate + ", password=***]";
    }
  }

  /**
   * Returns which way this is.
   *
   * @return the type, as named in the wire protocol
   */
  default AuthenticationType type() {
    return switch (this) {
      case NoPassword _ -> AuthenticationType.NONE;
      case Password _ -> AuthenticationType.PASSWORD;
      case Certificate _ -> AuthenticationType.CERTIFICATE;
      case CertificateAndPassword _ -> AuthenticationType.CERTIFICATE_AND_PASSWORD;
    };
  }

  /**
   * Tells whether the client relies on the certificate alone, with no password that it could give
   * to the device.
   *
   * @return {@code true} only for {@link Certificate}
   */
  default boolean certificateAlone() {
    return this instanceof Certificate;
  }

  /**
   * Returns the client certificate to offer to the device.
   *
   * @return the certificate, or empty if none is offered
   */
  default Optional<ClientCertificate> certificateToOffer() {
    return switch (this) {
      case NoPassword _, Password _ -> Optional.empty();
      case Certificate variant -> Optional.of(variant.certificate());
      case CertificateAndPassword variant -> Optional.of(variant.certificate());
    };
  }

  /**
   * Returns the password to answer the device's challenge with. A password hash is only ever sent
   * when the caller supplied a password.
   *
   * @return the password, or empty if the caller supplied none
   */
  default Optional<String> passwordForChallenge() {
    return switch (this) {
      case NoPassword _, Certificate _ -> Optional.empty();
      case Password variant -> Optional.of(variant.password());
      case CertificateAndPassword variant -> Optional.of(variant.password());
    };
  }
}
