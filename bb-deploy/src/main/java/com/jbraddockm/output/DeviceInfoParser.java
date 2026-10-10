package com.jbraddockm.output;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Parses {@code -listDeviceInfo}. Observed format:
 *
 * <pre>
 * [n]&#64;deviceproperties section header, the [n] prefix is optional
 * key::value property with an empty type
 * key:b:true  key:n:1  typed property
 * </pre>
 *
 * <p>A section name can appear more than once ({@code deviceproperties} appears twice with
 * different keys). The blocks are merged, and a later duplicate key overwrites the earlier one.
 * Properties before the first header belong to the section named by the empty string.
 */
final class DeviceInfoParser implements OutputParser<DeviceInfo> {

  @Override
  public DeviceInfo parse(List<String> lines) {
    var sections = new LinkedHashMap<String, Map<String, DeviceInfo.Property>>();
    var unparsed = new ArrayList<String>();
    for (var entry : OutputText.readSections(lines)) {
      switch (entry) {
        case OutputText.SectionEntry.Header(var name) ->
            sections.computeIfAbsent(name, _ -> new LinkedHashMap<>());
        case OutputText.SectionEntry.Body(var section, var text, var raw) ->
            OutputText.parseProperty(text)
                .ifPresentOrElse(
                    property ->
                        sections
                            .computeIfAbsent(section, _ -> new LinkedHashMap<>())
                            .put(
                                property.key(),
                                new DeviceInfo.Property(property.type(), property.value())),
                    () -> unparsed.add(raw));
      }
    }
    return new DeviceInfo(sections, unparsed);
  }
}
