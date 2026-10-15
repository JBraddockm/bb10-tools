package com.jbraddockm.link.xml;

import java.io.StringReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * Parses a document into a tree of {@link XmlElement elements}.
 *
 * <p>Document type declarations and external entities are not processed, so a hostile response
 * cannot make the daemon read files or open connections. The JDK's own implementation is created
 * directly, which needs no service lookup and no native-image configuration.
 */
public final class XmlParser {

  private XmlParser() {}

  /**
   * Parses a complete document.
   *
   * @param document the XML text
   * @return the root element
   * @throws MalformedXmlException if the text is empty or not well-formed
   */
  public static XmlElement parse(String document) {
    var factory = XMLInputFactory.newDefaultFactory();
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    var open = new ArrayDeque<Builder>();
    XmlElement root = null;
    try {
      var reader = factory.createXMLStreamReader(new StringReader(document));
      try {
        while (reader.hasNext()) {
          switch (reader.next()) {
            case XMLStreamConstants.START_ELEMENT -> {
              var builder = new Builder(reader.getLocalName());
              for (int index = 0; index < reader.getAttributeCount(); index++) {
                builder.attributes.put(
                    reader.getAttributeLocalName(index), reader.getAttributeValue(index));
              }
              open.push(builder);
            }
            case XMLStreamConstants.CHARACTERS,
                XMLStreamConstants.CDATA,
                XMLStreamConstants.SPACE -> {
              if (!open.isEmpty()) {
                open.peek().text.append(reader.getText());
              }
            }
            case XMLStreamConstants.END_ELEMENT -> {
              var finished = open.pop().build();
              if (open.isEmpty()) {
                root = finished;
              } else {
                open.peek().children.add(finished);
              }
            }
            default -> {
              // comments, processing instructions and the like carry no data
            }
          }
        }
      } finally {
        reader.close();
      }
    } catch (XMLStreamException failure) {
      throw new MalformedXmlException("not well-formed XML: " + failure.getMessage(), failure);
    }
    if (root == null) {
      throw new MalformedXmlException("the document has no root element", null);
    }
    return root;
  }

  /** An element that is still being read. */
  private static final class Builder {
    private final String name;
    private final SequencedMap<String, String> attributes = new LinkedHashMap<>();
    private final List<XmlElement> children = new ArrayList<>();
    private final StringBuilder text = new StringBuilder();

    Builder(String name) {
      this.name = name;
    }

    XmlElement build() {
      return new XmlElement(name, text.toString().strip(), attributes, children);
    }
  }
}
