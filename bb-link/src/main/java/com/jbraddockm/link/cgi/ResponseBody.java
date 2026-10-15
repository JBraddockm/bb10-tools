package com.jbraddockm.link.cgi;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.xml.MalformedXmlException;
import com.jbraddockm.link.xml.XmlJson;
import com.jbraddockm.link.xml.XmlParser;
import java.util.Objects;

/**
 * The body of a device answer, as a client receives it.
 *
 * <p>What the body is decided by its content, not by the content type, which a device may leave
 * out. Text that starts with {@code <} and is well-formed XML becomes JSON through {@link XmlJson};
 * text that starts with an opening brace or bracket and parses as JSON stays JSON; everything else,
 * including an answer that only looks like one of these, is kept as the text it was. So the value
 * is never an approximation of something that could not be read.
 *
 * @param format what the body was turned into
 * @param value the JSON-compatible value: a tree for {@link BodyFormat#JSON} and {@link
 *     BodyFormat#XML}, the text for {@link BodyFormat#TEXT}
 */
public record ResponseBody(BodyFormat format, Object value) {

  /**
   * Validates the body.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public ResponseBody {
    Objects.requireNonNull(format, "format");
    Objects.requireNonNull(value, "value");
  }

  /**
   * Reads a body.
   *
   * @param text the body as the device sent it
   * @return the body in the best form that can be read without loss of meaning
   */
  public static ResponseBody of(String text) {
    var trimmed = text.strip();
    try {
      if (trimmed.startsWith("<")) {
        return new ResponseBody(BodyFormat.XML, XmlJson.convert(XmlParser.parse(text)));
      }
      if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
        return new ResponseBody(BodyFormat.JSON, Json.parse(trimmed));
      }
    } catch (MalformedXmlException | Json.JsonException _) {
      // falls through: it only looked like XML or JSON
    }
    return new ResponseBody(BodyFormat.TEXT, text);
  }
}
