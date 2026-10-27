package com.jbraddockm.link.transport.session;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.xml.MalformedXmlException;
import com.jbraddockm.link.transport.xml.XmlElement;
import com.jbraddockm.link.transport.xml.XmlParser;
import java.util.Optional;

/** A parsed answer of the login interface. */
final class LoginDocument {

  private static final String LEGACY_ROOT = "RIM_tablet_response";

  private final XmlElement root;

  private LoginDocument(XmlElement root) {
    this.root = root;
  }

  static LoginDocument of(CgiResponse response) throws UnusableLoginResponseException {
    if (response.body().isBlank()) {
      throw new UnusableLoginResponseException("Did not get response");
    }
    try {
      return new LoginDocument(XmlParser.parse(response.body()));
    } catch (MalformedXmlException failure) {
      throw new UnusableLoginResponseException(
          "No content or invalid xml: " + failure.getMessage());
    }
  }

  private Optional<String> text(LoginKey key) {
    return root.descendant(key.elementName(root.name().equals(LEGACY_ROOT)))
        .map(XmlElement::text)
        .filter(value -> !value.isEmpty());
  }

  LoginStatus status() throws UnusableLoginResponseException {
    var text =
        text(LoginKey.STATUS)
            .orElseThrow(
                () -> new UnusableLoginResponseException("Cannot determine status of response"));
    return LoginStatus.of(text)
        .orElseThrow(() -> new UnusableLoginResponseException("Unknown status: " + text));
  }

  String required(LoginKey key) throws UnusableLoginResponseException {
    return text(key)
        .orElseThrow(
            () ->
                new UnusableLoginResponseException(
                    "The answer has no " + key.name().toLowerCase()));
  }

  int integer(LoginKey key) throws UnusableLoginResponseException {
    var value = required(key);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException _) {
      throw new UnusableLoginResponseException(
          "The " + key.name().toLowerCase() + " is not a number: " + value);
    }
  }

  /**
   * Reads the file share credentials of a successful login. The user and the password appear both
   * as attributes of {@code Credentials} and as elements; the elements are read first.
   */
  Optional<SmbCredentials> smb() {
    return root.descendant("Smb")
        .flatMap(
            smb -> {
              var credentials = smb.child("Credentials");
              var user =
                  smb.childText("User")
                      .or(() -> credentials.flatMap(found -> found.attribute("User")));
              var password =
                  smb.childText("Pwd")
                      .or(() -> credentials.flatMap(found -> found.attribute("Password")));
              if (user.isEmpty() || password.isEmpty()) {
                return Optional.empty();
              }
              return Optional.of(
                  new SmbCredentials(
                      user.get(),
                      password.get(),
                      smb.childText("Workgroup").orElse(null),
                      smb.childText("NetBios").orElse(null),
                      ""));
            });
  }

  PasswordRejectedException denial() throws UnusableLoginResponseException {
    return new PasswordRejectedException(
        integer(LoginKey.FAILED_ATTEMPTS), integer(LoginKey.RETRIES_REMAINING));
  }
}
