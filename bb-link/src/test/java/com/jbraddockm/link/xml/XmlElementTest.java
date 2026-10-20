package com.jbraddockm.link.xml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.link.testing.Resources;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link XmlElement}: searching a small shared tree. */
@DisplayName("XmlElement: the lookups on a tree of elements")
class XmlElementTest {

  private XmlElement library;

  @BeforeEach
  void readTheTree() {
    library = XmlParser.parse(Resources.text(XmlElementTest.class, "xml-element--tree.xml"));
  }

  @Nested
  @DisplayName("child and children")
  class Children {

    @Test
    @DisplayName("child gives the first child of that name")
    void childGivesTheFirst() {
      assertThat(library.child("Shelf"))
          .hasValueSatisfying(shelf -> assertThat(shelf.attribute("id")).contains("1"));
    }

    @Test
    @DisplayName("child gives nothing for a name that no child has, and does not look deeper")
    void childDoesNotLookDeeper() {
      assertThat(library.child("Book")).isEmpty();
    }

    @Test
    @DisplayName("children gives every child of that name, in document order")
    void childrenGivesAllInOrder() {
      assertThat(library.children("Shelf"))
          .extracting(shelf -> shelf.attribute("id").orElseThrow())
          .containsExactly("1", "2");
    }

    @Test
    @DisplayName("children gives an empty list for a name that no child has")
    void childrenGivesNothingForAnUnknownName() {
      assertThat(library.children("Magazine")).isEmpty();
    }
  }

  @Nested
  @DisplayName("at and textAt: a path of names")
  class Paths {

    @Test
    @DisplayName("at follows the first child of each name down the path")
    void atFollowsThePath() {
      assertThat(library.at("Shelf", "Book").map(XmlElement::text)).contains("First");
    }

    @Test
    @DisplayName("at with no names gives the element itself")
    void atWithoutNamesGivesTheElement() {
      assertThat(library.at()).containsSame(library);
    }

    @Test
    @DisplayName("at gives nothing if a name on the path is missing, wherever it is")
    void atGivesNothingForABrokenPath() {
      assertThat(library.at("Shelf", "Magazine", "Book")).isEmpty();
      assertThat(library.at("Magazine", "Book")).isEmpty();
    }

    @Test
    @DisplayName("textAt gives the text at the end of the path")
    void textAtGivesTheText() {
      assertThat(library.textAt("Title")).contains("Top");
    }

    @Test
    @DisplayName("textAt gives nothing if the text is empty, or only blanks")
    void textAtGivesNothingForEmptyText() {
      assertThat(library.textAt("Empty")).isEmpty();
      assertThat(library.textAt("Note")).isEmpty();
    }

    @Test
    @DisplayName("textAt gives nothing if the path is broken")
    void textAtGivesNothingForABrokenPath() {
      assertThat(library.textAt("Shelf", "Magazine")).isEmpty();
    }
  }

  @Nested
  @DisplayName("descendant")
  class Descendants {

    @Test
    @DisplayName(
        "descendant looks depth first, in document order: a deep match comes before a later one")
    void looksDepthFirst() {
      assertThat(library.descendant("Title").map(XmlElement::text)).contains("Deep");
    }

    @Test
    @DisplayName("descendant gives a child before anything deeper that comes after it")
    void givesAChildInItsPlace() {
      assertThat(library.descendant("Shelf").flatMap(shelf -> shelf.attribute("id"))).contains("1");
    }

    @Test
    @DisplayName("descendant gives nothing if no element has the name")
    void givesNothingForAnUnknownName() {
      assertThat(library.descendant("Magazine")).isEmpty();
    }

    @Test
    @DisplayName("descendant does not give the element itself")
    void doesNotGiveTheElementItself() {
      assertThat(library.descendant("Library")).isEmpty();
    }
  }

  @Nested
  @DisplayName("childText and attribute")
  class Values {

    @Test
    @DisplayName("childText gives the text of the first child of that name")
    void childTextGivesTheText() {
      assertThat(library.childText("Title")).contains("Top");
    }

    @Test
    @DisplayName("childText gives nothing for an empty child, a missing one, or one that is deeper")
    void childTextGivesNothing() {
      assertThat(library.childText("Empty")).isEmpty();
      assertThat(library.childText("Magazine")).isEmpty();
      assertThat(library.childText("Book")).isEmpty();
    }

    @Test
    @DisplayName("attribute gives the value of an attribute, and nothing for one that is not there")
    void attributeGivesTheValue() {
      assertThat(library.attribute("name")).contains("main");
      assertThat(library.attribute("closed")).isEmpty();
    }
  }

  @Nested
  @DisplayName("what an element is made of")
  class Construction {

    @Test
    @DisplayName("the attributes and the children cannot be changed through the element")
    void theCollectionsAreUnmodifiable() {
      assertThatThrownBy(() -> library.attributes().put("new", "x"))
          .isInstanceOf(UnsupportedOperationException.class);
      assertThatThrownBy(() -> library.children().add(library))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("the element keeps a copy: changing what it was made from changes nothing in it")
    void theElementKeepsACopy() {
      var attributes = new LinkedHashMap<String, String>();
      attributes.put("k", "v");
      var children = new ArrayList<XmlElement>();
      var element = new XmlElement("e", "", attributes, children);

      attributes.put("other", "x");
      children.add(library);

      assertThat(element.attributes()).containsOnlyKeys("k");
      assertThat(element.children()).isEqualTo(List.of());
    }
  }
}
