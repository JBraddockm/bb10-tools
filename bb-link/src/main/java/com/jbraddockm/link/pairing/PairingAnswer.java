package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.ResponseBody;
import java.util.Map;

/**
 * What {@code pairing.cgi} answered.
 *
 * @param contentType the {@code Content-Type} header, or {@code null} if the device sent none; the
 *     pairing service answers with XML
 * @param status the content of {@code Status}, for example {@code Success} or {@code Error}; may be
 *     {@code null} if the answer has none
 * @param errorIdentifier the number in {@code ErrorIdentifier}, or {@code null}
 * @param errorDescription the text of {@code ErrorDescription}, or {@code null}
 * @param body the body: the XML answer as JSON (see {@code XmlJson} for the mapping), or the text
 *     as sent if it is not XML
 */
public record PairingAnswer(
    String contentType,
    String status,
    Integer errorIdentifier,
    String errorDescription,
    ResponseBody body)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "contentType", contentType,
        "status", status,
        "errorIdentifier", errorIdentifier,
        "errorDescription", errorDescription,
        "bodyFormat", body.format().wireName(),
        "body", body.value());
  }
}
