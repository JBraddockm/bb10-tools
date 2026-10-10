package com.jbraddockm.output;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Parses {@code -listInstalledApps}. Observed format, with 231 apps captured:
 *
 * <pre>
 * &#64;applications
 * &lt;fullName&gt;::&lt;id&gt;,&lt;version&gt;,&lt;field2&gt;,&lt;field3&gt;,&lt;sizeBytes&gt;[,&lt;extra&gt;...]
 * </pre>
 *
 * <p>Field 2 was empty on every app and field 3 was {@code 1} on 44 of 231 with no visible link to
 * the other data, so both are kept unnamed. Extras are {@code key::value} attributes or bare-word
 * flags.
 */
final class InstalledAppsParser implements OutputParser<InstalledApps> {

  private static final int REQUIRED_FIELDS = 5;
  private static final int FIRST_EXTRA = 5;

  private static Optional<InstalledApp> parseApp(String text) {
    return OutputText.parseProperty(text)
        .filter(property -> property.type().isEmpty())
        .flatMap(property -> fromFields(property.key(), property.value().split(",", -1)));
  }

  private static Optional<InstalledApp> fromFields(String fullName, String[] fields) {
    if (fields.length < REQUIRED_FIELDS || fields[0].isEmpty()) {
      return Optional.empty();
    }
    var id = fields[0];
    var extras =
        Arrays.stream(fields)
            .skip(FIRST_EXTRA)
            .filter(extra -> !extra.isEmpty())
            .collect(Collectors.partitioningBy(extra -> extra.contains("::")));
    var attributes =
        extras.get(true).stream()
            .collect(
                Collectors.toMap(
                    extra -> extra.substring(0, extra.indexOf("::")),
                    extra -> extra.substring(extra.indexOf("::") + 2),
                    (_, latest) -> latest,
                    LinkedHashMap::new));
    return Optional.of(
        new InstalledApp(
            fullName,
            OutputText.packageName(fullName, id).orElse(null),
            id,
            fields[1],
            OutputText.parseLong(fields[4]).orElse(null),
            attributes,
            extras.get(false),
            List.of(fields[2], fields[3])));
  }

  @Override
  public InstalledApps parse(List<String> lines) {
    var apps = new ArrayList<InstalledApp>();
    var unparsed = new ArrayList<String>();
    for (var entry : OutputText.readSections(lines)) {
      if (!(entry instanceof OutputText.SectionEntry.Body(var section, var text, var raw))) {
        continue; // a header carries no payload
      }
      var app = section.equals("applications") ? parseApp(text) : Optional.<InstalledApp>empty();
      app.ifPresentOrElse(apps::add, () -> unparsed.add(raw));
    }
    return new InstalledApps(apps, unparsed);
  }
}
