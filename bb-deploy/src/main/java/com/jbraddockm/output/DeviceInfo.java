package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The properties a device reports, grouped into sections.
 *
 * @param sections the properties by section name and key; a section that appears several times in
 *     the output is merged
 * @param unparsed lines that are not properties, exactly as printed
 */
public record DeviceInfo(Map<String, Map<String, Property>> sections, List<String> unparsed)
    implements CommandResult {

  /**
   * Takes deep, immutable, order-preserving copies of the collections.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public DeviceInfo {
    var copy = new LinkedHashMap<String, Map<String, Property>>();
    Objects.requireNonNull(sections, "sections")
        .forEach((name, properties) -> copy.put(name, OutputText.orderedCopy(properties)));
    sections = OutputText.orderedCopy(copy);
    unparsed = List.copyOf(unparsed);
  }

  /**
   * Finds a property by key in whichever section holds it.
   *
   * <p>The device keeps, for example, {@code hostname} under {@code versions} and {@code modelname}
   * under {@code deviceproperties}; callers should not depend on that layout.
   *
   * @param key the property key
   * @return the first non-blank value with that key, or empty
   */
  public Optional<String> find(String key) {
    return sections.values().stream()
        .map(properties -> properties.get(key))
        .filter(Objects::nonNull)
        .map(Property::value)
        .flatMap(value -> OutputText.nonBlank(value).stream())
        .findFirst();
  }

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "sections",
        OutputText.mapValues(
            sections, properties -> OutputText.mapValues(properties, Property::toJson)),
        "unparsed",
        unparsed);
  }

  /**
   * One property.
   *
   * @param type the device's type tag: empty, {@code b} (boolean) or {@code n} (number)
   * @param value the raw trimmed value; for {@code b} observed values are {@code true}, {@code
   *     false} and empty
   */
  public record Property(String type, String value) {

    /**
     * Returns the JSON-compatible representation.
     *
     * @return a map with {@code type} and {@code value}
     */
    public Map<String, Object> toJson() {
      return Json.object("type", type, "value", value);
    }
  }
}
