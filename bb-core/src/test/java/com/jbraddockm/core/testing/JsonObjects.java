package com.jbraddockm.core.testing;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reads the parsed JSON of a response or an event into typed maps and lists. */
public final class JsonObjects {

  private JsonObjects() {}

  /**
   * Returns a parsed JSON object with its keys as text.
   *
   * @param value a parsed JSON object
   * @return the members, in order
   */
  public static Map<String, Object> object(Object value) {
    var result = new LinkedHashMap<String, Object>();
    ((Map<?, ?>) value).forEach((key, entry) -> result.put(String.valueOf(key), entry));
    return result;
  }

  /**
   * Returns a parsed JSON array.
   *
   * @param value a parsed JSON array
   * @return a copy of its items
   */
  public static List<Object> array(Object value) {
    return List.copyOf((List<?>) value);
  }
}
