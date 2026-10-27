package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * A perimeter of the device.
 *
 * @param kind what kind of perimeter it is
 * @param bbid the BlackBerry ID bound to the perimeter; may be {@code null}. Identifies the owner,
 *     and is the key a backup manifest carries.
 */
record Perimeter(PerimeterKind kind, String bbid) {

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map; an absent ID is {@code null}
   */
  public Map<String, Object> toJson() {
    return Json.object("kind", kind.jsonName(), "bbid", bbid);
  }
}
