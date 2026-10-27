package com.jbraddockm.link.transport.xml;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns an XML element into a JSON-compatible tree with a fixed, simple mapping.
 *
 * <p>The mapping cannot be lossless, because XML has no types and no arrays, so it is deliberately
 * plain:
 *
 * <ul>
 *   <li>An element without attributes and children becomes its text, a string, empty if there is no
 *       text. The text is never turned into a number or a boolean.
 *   <li>An element with children becomes an object with one member per child name, in document
 *       order.
 *   <li>A name that occurs more than once among the children becomes an array of its values. A name
 *       that occurs once is the value itself, so a single child cannot be told from a list of one.
 *   <li>An attribute becomes a member whose name starts with {@code @}. If an element has
 *       attributes or children and also text, the text is the member {@code #text}.
 *   <li>The name of the element that is converted is dropped: only its content is returned.
 *   <li>Comments and processing instructions are not part of the tree.
 * </ul>
 */
public final class XmlJson {

  private static final String TEXT_MEMBER = "#text";
  private static final String ATTRIBUTE_PREFIX = "@";

  private XmlJson() {}

  /**
   * Converts the content of an element.
   *
   * @param element the element; its own name is not part of the result
   * @return a string, or a map with insertion order, whose values are strings, maps and lists
   */
  public static Object convert(XmlElement element) {
    if (element.attributes().isEmpty() && element.children().isEmpty()) {
      return element.text();
    }
    var object = new LinkedHashMap<String, Object>();
    element.attributes().forEach((name, value) -> object.put(ATTRIBUTE_PREFIX + name, value));
    for (var child : element.children()) {
      add(object, child.name(), convert(child));
    }
    if (!element.text().isEmpty()) {
      object.put(TEXT_MEMBER, element.text());
    }
    return object;
  }

  /** Adds a value; a second value under the same name turns the member into an array. */
  private static void add(Map<String, Object> object, String name, Object value) {
    var existing = object.get(name);
    if (existing == null) {
      object.put(name, value);
    } else if (existing instanceof List<?> list) {
      @SuppressWarnings("unchecked") // only lists made here are ever stored, and they hold Objects
      var values = (List<Object>) list;
      values.add(value);
    } else {
      var values = new ArrayList<Object>();
      values.add(existing);
      values.add(value);
      object.put(name, values);
    }
  }
}
