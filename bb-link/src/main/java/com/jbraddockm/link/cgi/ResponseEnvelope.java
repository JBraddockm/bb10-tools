package com.jbraddockm.link.cgi;

import com.jbraddockm.link.xml.MalformedXmlException;
import com.jbraddockm.link.xml.XmlElement;
import com.jbraddockm.link.xml.XmlParser;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * A parsed device response with its envelope removed.
 *
 * <p>Devices wrap the answer in a root element (named {@code RimTabletResponse} or {@code
 * RIM_tablet_response}), and some firmware adds a {@code Rev} element inside it. The payload is the
 * first element that is neither of those wrappers.
 *
 * @param payload the element that names the kind of answer
 */
public record ResponseEnvelope(XmlElement payload) {

  private static final String REVISION = "Rev";

  /**
   * Parses a response body.
   *
   * @param document the XML text
   * @return the envelope
   * @throws MalformedXmlException if the text is not well-formed XML
   */
  public static ResponseEnvelope parse(String document) {
    var root = XmlParser.parse(document);
    if (root.children().isEmpty()) {
      return new ResponseEnvelope(root);
    }
    var first = root.children().getFirst();
    if (!first.name().equals(REVISION)) {
      return new ResponseEnvelope(first);
    }
    // Candidates after a revision marker: what it contains, then what follows it.
    var candidates = Stream.concat(first.children().stream(), root.children().stream().skip(1));
    return new ResponseEnvelope(
        candidates.filter(element -> !element.name().equals(REVISION)).findFirst().orElse(first));
  }

  /**
   * Returns the name of the payload element.
   *
   * @return the element name as written
   */
  public String name() {
    return payload.name();
  }

  /**
   * Recognises the payload element.
   *
   * @return the known element, or empty if this version does not know the name
   */
  public Optional<ResponseElement> element() {
    return ResponseElement.of(payload.name());
  }
}
