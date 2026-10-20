package com.jbraddockm.link.xml;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link XmlJson}: converting XML trees to JSON. */
@DisplayName("XmlJson: turns a tree of XML elements into JSON")
class XmlJsonTest {

  @ParameterizedTest(name = "{0}: {1}")
  @CsvSource({
    "text-only, an element with text only is a string",
    "empty-element, an empty element is an empty string",
    "children, a number stays text and members follow the document",
    "attribute, an attribute is a member that starts with @",
    "attributes-and-text, attributes and text: the text is #text",
    "repeated-name, a repeated name becomes an array",
    "three-of-a-name, three of a name make an array of three",
    "nested, elements inside elements are objects inside objects",
    "text-and-children, text next to children is #text after the children",
    "attribute-order, attributes come first and keep their order",
    "member-order, a repeated name keeps the place of its first member"
  })
  @DisplayName("convert gives the JSON that the pair of files says")
  void convertGivesTheJsonOfTheFile(String name, String rule) {
    var document = XmlParser.parse(Resources.text(XmlJsonTest.class, "xml-json--" + name + ".xml"));
    var expected = Resources.text(XmlJsonTest.class, "xml-json--" + name + ".json").strip();

    assertThat(Json.write(XmlJson.convert(document))).as(rule).isEqualTo(expected);
  }
}
