package com.jbraddockm.link.testing;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Decodes the form bodies received by a {@link FakeDevice}. */
public final class FormBodies {

  private FormBodies() {}

  /**
   * The fields of a form body, decoded, in the order they were sent.
   *
   * @param body {@code name=value} pairs joined by {@code &}, with the values encoded
   * @return the fields
   */
  public static Map<String, String> parse(String body) {
    var fields = new LinkedHashMap<String, String>();
    for (var pair : body.split("&")) {
      var parts = pair.split("=", 2);
      fields.put(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
    }
    return fields;
  }
}
