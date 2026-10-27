package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.endpoint.pairing.pki.Pem;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code login.cgi} answered to a certificate request. Its shape has not been seen, so the
 * answer is read as loosely as possible: a certificate is the first one in the text of the answer,
 * wherever it sits, and the status and the error are those of an XML answer, if it is one.
 *
 * @param contentType the content type of the answer
 * @param statusText the text of {@code Status}, as the device wrote it; {@code null} if there is
 *     none
 * @param certificate the first certificate in the answer, as the device wrote it
 * @param errorIdentifier the number of the error; {@code null} if there is none
 * @param errorDescription the description of the error; {@code null} if there is none
 * @param body the whole answer
 */
public record AuthenticationAnswer(
    String contentType,
    String statusText,
    Optional<String> certificate,
    Integer errorIdentifier,
    String errorDescription,
    ResponseBody body)
    implements CommandResult {

  /**
   * Reads an answer.
   *
   * @param response the answer
   * @param envelope its XML, if it is XML
   * @return what it says
   */
  static AuthenticationAnswer read(CgiResponse response, ResponseEnvelope envelope) {
    var payload = Optional.ofNullable(envelope).map(ResponseEnvelope::payload);
    var statusText = payload.flatMap(element -> element.childText("Status")).orElse(null);
    var identifier =
        payload
            .flatMap(element -> element.textAt("Error", "ErrorIdentifier"))
            .flatMap(AuthenticationAnswer::integer)
            .orElse(null);
    var description =
        payload.flatMap(element -> element.textAt("Error", "ErrorDescription")).orElse(null);
    var certificate =
        Pem.blocks(response.body(), "CERTIFICATE").stream().findFirst().map(Pem.Block::text);
    return new AuthenticationAnswer(
        response.contentType(),
        statusText,
        certificate,
        identifier,
        description,
        ResponseBody.of(response.body()));
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "contentType", contentType,
        "status", statusText,
        "certificate", certificate.orElse(null),
        "errorIdentifier", errorIdentifier,
        "errorDescription", errorDescription,
        "bodyFormat", body.format().wireName(),
        "body", body.value());
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.parseInt(text.strip()));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
