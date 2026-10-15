package com.jbraddockm.link.xml;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.SequencedMap;

/**
 * One element of a parsed document.
 *
 * @param name the element name as written
 * @param text the character data directly inside the element, stripped of surrounding whitespace;
 *     empty if there is none
 * @param attributes the attributes in document order
 * @param children the child elements in document order
 */
public record XmlElement(
    String name, String text, SequencedMap<String, String> attributes, List<XmlElement> children) {

  /** Takes immutable copies of the collections. */
  public XmlElement {
    attributes = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(attributes));
    children = List.copyOf(children);
  }

  /**
   * Finds the first child with the given name.
   *
   * @param childName the element name to look for
   * @return the child, or empty
   */
  public Optional<XmlElement> child(String childName) {
    return children.stream().filter(child -> child.name.equals(childName)).findFirst();
  }

  /**
   * Follows a chain of child elements.
   *
   * @param names the names to follow, outermost first
   * @return the element at the end of the chain, or empty if any link is missing
   */
  public Optional<XmlElement> at(String... names) {
    var current = Optional.of(this);
    for (var name : names) {
      current = current.flatMap(element -> element.child(name));
    }
    return current;
  }

  /**
   * Reads the text of an element reached through a chain of children.
   *
   * @param names the names to follow, outermost first
   * @return the text; empty if the chain is broken or the text is empty
   */
  public Optional<String> textAt(String... names) {
    return at(names).map(XmlElement::text).filter(value -> !value.isEmpty());
  }

  /**
   * Finds all children with the given name.
   *
   * @param childName the element name to look for
   * @return the children in document order
   */
  public List<XmlElement> children(String childName) {
    return children.stream().filter(child -> child.name.equals(childName)).toList();
  }

  /**
   * Finds the first descendant with the given name in document order, not counting this element.
   *
   * @param descendantName the element name to look for
   * @return the descendant, or empty
   */
  public Optional<XmlElement> descendant(String descendantName) {
    for (var child : children) {
      if (child.name.equals(descendantName)) {
        return Optional.of(child);
      }
      var nested = child.descendant(descendantName);
      if (nested.isPresent()) {
        return nested;
      }
    }
    return Optional.empty();
  }

  /**
   * Reads the text of a child element.
   *
   * @param childName the element name to look for
   * @return the text; empty if there is no such child or its text is empty
   */
  public Optional<String> childText(String childName) {
    return child(childName).map(XmlElement::text).filter(value -> !value.isEmpty());
  }

  /**
   * Reads an attribute.
   *
   * @param attributeName the attribute name
   * @return the value, or empty
   */
  public Optional<String> attribute(String attributeName) {
    return Optional.ofNullable(attributes.get(attributeName));
  }
}
