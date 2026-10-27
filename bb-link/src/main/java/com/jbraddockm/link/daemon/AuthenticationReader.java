package com.jbraddockm.link.daemon;

import com.jbraddockm.core.daemon.RequestParams;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.session.Authentication;
import com.jbraddockm.link.transport.session.AuthenticationType;
import com.jbraddockm.link.transport.session.ClientCertificate;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the {@code authentication} object of a {@code connect} request.
 *
 * <p>The object names its type and carries exactly the fields of that type. A field that does not
 * belong to the type is refused instead of ignored, so a client that meant a different type finds
 * out at once: a password sent with the type {@code none} is an error, not a login without a
 * password. Error parameter names are written as {@code authentication.<field>}.
 */
final class AuthenticationReader {

  private static final String OBJECT = "authentication";

  /** The parameters that used to sit beside {@code host} and now belong inside the object. */
  private static final List<String> MOVED = List.of("password", "clientCertificate", "clientKey");

  private AuthenticationReader() {}

  static Authentication read(RequestParams connectParams) {
    for (var name : MOVED) {
      if (connectParams.names().contains(name)) {
        throw new InvalidArgumentException(
            name,
            "is now part of 'authentication', for example "
                + "{\"authentication\":{\"type\":\"password\",\"password\":\"…\"}}");
      }
    }
    var object = connectParams.requireObject(OBJECT);
    var type =
        AuthenticationType.of(
            string(object, "type")
                .orElseThrow(() -> new InvalidArgumentException(OBJECT + ".type", "is required")));
    refuseOtherFields(object, type);
    return switch (type) {
      case NONE -> new Authentication.NoPassword();
      case PASSWORD -> new Authentication.Password(string(object, "password").orElse(null));
      case CERTIFICATE -> new Authentication.Certificate(certificate(object, type));
      case CERTIFICATE_AND_PASSWORD ->
          new Authentication.CertificateAndPassword(
              certificate(object, type), string(object, "password").orElse(null));
    };
  }

  private static Set<String> fieldsOf(AuthenticationType type) {
    return switch (type) {
      case NONE -> Set.of("type");
      case PASSWORD -> Set.of("type", "password");
      case CERTIFICATE -> Set.of("type", "clientCertificate", "clientKey");
      case CERTIFICATE_AND_PASSWORD -> Set.of("type", "clientCertificate", "clientKey", "password");
    };
  }

  private static void refuseOtherFields(RequestParams object, AuthenticationType type) {
    var allowed = fieldsOf(type);
    for (var name : object.names()) {
      if (!allowed.contains(name)) {
        throw new InvalidArgumentException(
            OBJECT + "." + name, "is not accepted with the type '" + type.wireName() + "'");
      }
    }
  }

  private static ClientCertificate certificate(RequestParams object, AuthenticationType type) {
    return new ClientCertificate(
        path(object, "clientCertificate", type), path(object, "clientKey", type));
  }

  private static Optional<String> string(RequestParams object, String name) {
    try {
      return object.optionalString(name);
    } catch (InvalidArgumentException _) {
      throw new InvalidArgumentException(OBJECT + "." + name, "must be a string");
    }
  }

  private static Path path(RequestParams object, String name, AuthenticationType type) {
    var text =
        string(object, name)
            .filter(value -> !value.isBlank())
            .orElseThrow(
                () ->
                    new InvalidArgumentException(
                        OBJECT + "." + name,
                        "is required with the type '" + type.wireName() + "'"));
    try {
      return Path.of(text);
    } catch (InvalidPathException _) {
      throw new InvalidArgumentException(OBJECT + "." + name, "is not a valid path");
    }
  }
}
