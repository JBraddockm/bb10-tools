package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.ResponseBody;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseStatus;
import com.jbraddockm.link.xml.XmlElement;
import java.util.Map;
import java.util.Optional;

/**
 * What {@code pairing.cgi} answered: the status, the device's authority if there is one, the error
 * if there is one, and the whole answer as JSON.
 *
 * @param contentType the content type of the answer
 * @param statusText the text of {@code Status}, as the device wrote it; {@code null} if there is
 *     none
 * @param status the status as one of the known words; an answer that has an authority and no {@code
 *     Status} is a success
 * @param deviceAuthority the device's own authority, as the device wrote it, if the answer has one:
 *     it is the text of {@code Pairing}, in a CDATA section, with no {@code Status} **[verified]**
 * @param errorIdentifier the number of the error; {@code null} if there is none
 * @param errorDescription the description of the error; {@code null} if there is none
 * @param body the whole answer
 */
public record PairingReply(
    String contentType,
    String statusText,
    ResponseStatus status,
    Optional<String> deviceAuthority,
    Integer errorIdentifier,
    String errorDescription,
    ResponseBody body)
    implements CommandResult {

  /**
   * Reads an answer that is XML.
   *
   * @param response the answer
   * @param envelope its XML
   * @return what it says; {@code Status} is looked for wherever it sits, as the clients do
   */
  static PairingReply read(CgiResponse response, ResponseEnvelope envelope) {
    var payload = envelope.payload();
    var statusText = text(payload, "Status").orElse(null);
    var authority =
        text(payload, "Certificate")
            .filter(text -> !text.isBlank())
            .or(() -> certificateIn(payload.text()));
    var identifier = text(payload, "ErrorIdentifier").flatMap(PairingReply::integer).orElse(null);
    var description = text(payload, "ErrorDescription").orElse(null);
    var status = ResponseStatus.of(statusText);
    if (statusText == null && authority.isPresent() && identifier == null) {
      // what the device really answers to an insert that it takes: a certificate, and no status
      status = ResponseStatus.SUCCESS;
    }
    return new PairingReply(
        response.contentType(),
        statusText,
        status,
        authority,
        identifier,
        description,
        ResponseBody.of(response.body()));
  }

  /**
   * Keeps an answer that is not XML.
   *
   * @param response the answer
   * @return a reply with an unknown status and the text as the body
   */
  static PairingReply unreadable(CgiResponse response) {
    return new PairingReply(
        response.contentType(),
        null,
        ResponseStatus.UNKNOWN,
        Optional.empty(),
        null,
        null,
        ResponseBody.of(response.body()));
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "contentType", contentType,
        "status", statusText,
        "deviceAuthority", deviceAuthority.orElse(null),
        "errorIdentifier", errorIdentifier,
        "errorDescription", errorDescription,
        "bodyFormat", body.format().wireName(),
        "body", body.value());
  }

  /** A certificate that is the text of the element itself, which is where the device puts one. */
  private static Optional<String> certificateIn(String text) {
    return text != null && text.contains("-----BEGIN CERTIFICATE-----")
        ? Optional.of(text.strip())
        : Optional.empty();
  }

  private static Optional<String> text(XmlElement payload, String name) {
    return payload
        .childText(name)
        .or(() -> payload.descendant(name).map(XmlElement::text))
        .map(String::strip);
  }

  private static Optional<Integer> integer(String text) {
    try {
      return Optional.of(Integer.parseInt(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }
}
