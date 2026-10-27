package com.jbraddockm.link.transport.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import com.jbraddockm.link.testing.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link XmlParser}: elements, attributes, text, and rejected input. */
@DisplayName("XmlParser: reads a document into a tree of elements")
class XmlParserTest {

  private static String document(String name) {
    return Resources.text(XmlParserTest.class, name);
  }

  @Nested
  @DisplayName("a well-formed document")
  class WellFormed {

    @Test
    @DisplayName("gives the root, and the children in document order")
    void givesTheTreeInDocumentOrder() {
      var root = XmlParser.parse(document("xml-parser--nested.xml"));

      assertThat(root.name()).isEqualTo("Root");
      assertThat(root.children())
          .extracting(XmlElement::name)
          .containsExactly("Child", "Child", "Group");
    }

    @Test
    @DisplayName("keeps the attributes in the order they are written, not sorted")
    void keepsTheOrderOfAttributes() {
      var root = XmlParser.parse(document("xml-parser--nested.xml"));

      assertThat(root.attributes()).containsExactly(Map.entry("z", "1"), Map.entry("a", "2"));
    }

    @Test
    @DisplayName("strips the blanks around the text of an element")
    void stripsTheBlanksAroundText() {
      var root = XmlParser.parse(document("xml-parser--nested.xml"));

      assertThat(root.children().getFirst().text()).isEqualTo("text");
    }

    @Test
    @DisplayName("gives an empty element an empty text, and no children")
    void givesAnEmptyElementAnEmptyText() {
      var empty = XmlParser.parse(document("xml-parser--nested.xml")).children().get(1);

      assertThat(empty.text()).isEmpty();
      assertThat(empty.children()).isEmpty();
    }

    @Test
    @DisplayName("reads the elements inside elements, at any depth")
    void readsNestedElements() {
      var group = XmlParser.parse(document("xml-parser--nested.xml")).children().get(2);

      assertThat(group.children()).extracting(XmlElement::text).containsExactly("deep");
    }

    @Test
    @DisplayName("ignores comments")
    void ignoresComments() {
      var root = XmlParser.parse(document("xml-parser--nested.xml"));

      assertThat(root.children()).hasSize(3);
      assertThat(root.text()).isEmpty();
    }

    @Test
    @DisplayName("takes a CDATA section as text, and does not read it as markup")
    void takesCdataAsText() {
      assertThat(XmlParser.parse(document("xml-parser--cdata.xml")).text())
          .isEqualTo(
              Resources.text(XmlParserTest.class, "xml-parser--cdata--expected.txt").strip());
    }

    @Test
    @DisplayName("resolves the predefined entities and character references")
    void resolvesPredefinedEntities() {
      assertThat(XmlParser.parse(document("xml-parser--entities.xml")).text())
          .isEqualTo("a & b < c A");
    }

    @Test
    @DisplayName("accepts an XML declaration before the root")
    void acceptsADeclaration() {
      assertThat(XmlParser.parse(document("xml-parser--declaration.xml")).text()).isEqualTo("x");
    }

    @Test
    @DisplayName("joins the text on both sides of a child, as the text of the element")
    void joinsTheTextAroundAChild() {
      var root = XmlParser.parse(document("xml-parser--mixed-content.xml"));

      assertThat(root.text()).isEqualTo("onetwo");
      assertThat(root.children()).hasSize(1);
    }

    @Test
    @DisplayName("is not aware of namespaces: a name is what is written, prefix included")
    void isNotAwareOfNamespaces() {
      var root = XmlParser.parse(document("xml-parser--namespaced.xml"));

      assertThat(List.of(root.name(), root.children().getFirst().name()))
          .containsExactly("ns:Root", "ns:Child");
    }
  }

  @Nested
  @DisplayName("a document that is not well-formed")
  class Malformed {

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(
        strings = {
          "xml-parser--malformed--empty.xml",
          "xml-parser--malformed--unclosed.xml",
          "xml-parser--malformed--plain-text.xml",
          "xml-parser--malformed--mismatched.xml",
          "xml-parser--malformed--undefined-entity.xml",
          "xml-parser--malformed--only-a-comment.xml"
        })
    @DisplayName("is refused with a MalformedXmlException")
    void isRefused(String resource) {
      var text = document(resource);

      assertThatExceptionOfType(MalformedXmlException.class)
          .isThrownBy(() -> XmlParser.parse(text));
    }

    @Test
    @DisplayName("says that the XML is not well-formed, and keeps the cause")
    void saysWhatIsWrong() {
      var text = document("xml-parser--malformed--mismatched.xml");

      assertThatExceptionOfType(MalformedXmlException.class)
          .isThrownBy(() -> XmlParser.parse(text))
          .withMessageStartingWith("not well-formed XML:")
          .withCauseInstanceOf(XMLStreamException.class);
    }
  }

  @Nested
  @DisplayName("a hostile document")
  class Hostile {

    @Test
    @DisplayName("does not read a file through an external entity")
    void doesNotReadFiles(@TempDir Path folder) throws Exception {
      var secret = Files.writeString(folder.resolve("secret.txt"), "the-secret-content");
      var text =
          Resources.text(
              XmlParserTest.class,
              "xml-parser--hostile--external-entity.xml",
              Map.of("secret-file", secret.toString()));

      String found;
      try {
        found = XmlParser.parse(text).text();
      } catch (MalformedXmlException _) {
        found = "";
      }

      assertThat(found).doesNotContain("the-secret-content");
    }

    @Test
    @DisplayName("does not expand entities that are defined in the document")
    void doesNotExpandEntities() {
      var text = document("xml-parser--hostile--entity-expansion.xml");

      String found;
      try {
        found = XmlParser.parse(text).text();
      } catch (MalformedXmlException _) {
        found = "";
      }

      assertThat(found).doesNotContain("aaaaaaaaaa");
    }
  }
}
