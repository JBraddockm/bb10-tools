package com.jbraddockm.output;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Text conventions of the tool's output, shared by the parsers of this package. */
final class OutputText {

  static final String INFO_PREFIX = "Info: ";
  private static final String ERROR_PREFIX = "Error: ";
  private static final String RESULT_PREFIX = "result::";
  private static final String ACTUAL_PREFIX = "actual_";
  private static final String DEVICE_MARKER = "[n]";
  private static final Pattern PROPERTY_TYPE = Pattern.compile("[A-Za-z]{0,3}");

  private OutputText() {}

  /**
   * Some device lines carry a {@code [n]} prefix whose meaning is not identified. It is dropped.
   */
  static String withoutDeviceMarker(String line) {
    return line.startsWith(DEVICE_MARKER) ? line.substring(DEVICE_MARKER.length()) : line;
  }

  /** Tool messages, status and result markers, and blank lines never belong to a payload. */
  static boolean isNoise(String line) {
    return line.isEmpty()
        || line.startsWith(INFO_PREFIX)
        || line.startsWith(ERROR_PREFIX)
        || line.startsWith(RESULT_PREFIX)
        || line.startsWith(ACTUAL_PREFIX);
  }

  /** Splits an output into section headers and the lines below them, dropping noise. */
  static List<SectionEntry> readSections(List<String> lines) {
    var entries = new ArrayList<SectionEntry>();
    var section = "";
    for (var raw : lines) {
      var text = withoutDeviceMarker(raw);
      if (isNoise(text)) {
        continue;
      }
      if (text.startsWith("@")) {
        section = text.substring(1);
        entries.add(new SectionEntry.Header(section));
      } else {
        entries.add(new SectionEntry.Body(section, text, raw));
      }
    }
    return entries;
  }

  /**
   * Splits on the first two colons into key, type and value, each trimmed (the device prints, for
   * example, {@code build_id:: 911537}). The type must be empty or one to three letters; anything
   * else is not a property, so a line such as {@code Build Branch: BB10_3_3} is not misread.
   */
  static Optional<PropertyLine> parseProperty(String line) {
    var firstColon = line.indexOf(':');
    if (firstColon <= 0) {
      return Optional.empty();
    }
    var secondColon = line.indexOf(':', firstColon + 1);
    if (secondColon < 0) {
      return Optional.empty();
    }
    var key = line.substring(0, firstColon).strip();
    var type = line.substring(firstColon + 1, secondColon).strip();
    var value = line.substring(secondColon + 1).strip();
    return key.isEmpty() || !PROPERTY_TYPE.matcher(type).matches()
        ? Optional.empty()
        : Optional.of(new PropertyLine(key, type, value));
  }

  /** The value of the last {@code result::<value>} line: the device's own verdict. */
  static Optional<String> lastResult(List<String> lines) {
    return lines.stream()
        .map(String::strip)
        .filter(line -> line.startsWith(RESULT_PREFIX))
        .map(line -> line.substring(RESULT_PREFIX.length()).strip())
        .reduce((_, latest) -> latest);
  }

  /** The value after the last line that starts with {@code prefix}, such as {@code actual_id::}. */
  static Optional<String> lastValueAfter(List<String> lines, String prefix) {
    return lines.stream()
        .map(String::strip)
        .filter(line -> line.startsWith(prefix))
        .map(line -> line.substring(prefix.length()))
        .reduce((_, latest) -> latest);
  }

  /**
   * The trimmed text, or empty if it is blank (a failed install prints {@code actual_id::} with no
   * value).
   */
  static Optional<String> nonBlank(String text) {
    return text == null || text.isBlank() ? Optional.empty() : Optional.of(text.strip());
  }

  /**
   * The package name, obtained by removing {@code .} and the id (with {@code -} written as {@code
   * .}) from the end of the full name. Ids are not always 27 characters long (a 28-character
   * Android id exists), so the length is never assumed.
   */
  static Optional<String> packageName(String fullName, String id) {
    if (fullName == null || id == null) {
      return Optional.empty();
    }
    var suffix = "." + id.replace('-', '.');
    return fullName.endsWith(suffix)
        ? Optional.of(fullName.substring(0, fullName.length() - suffix.length()))
        : Optional.empty();
  }

  /**
   * The device answers {@code result::Invalid} when it cannot act on the package, for example when
   * launching an app that is not installed, and the tool still exits with status 0.
   */
  static Optional<CommandFailure> invalidResult(String action, List<String> lines) {
    return lastResult(lines)
        .filter("Invalid"::equalsIgnoreCase)
        .map(
            result ->
                new CommandFailure(
                    action + " failed: the device reported '" + result + "'",
                    Map.of("result", result)));
  }

  static Optional<Long> parseLong(String text) {
    try {
      return Optional.of(Long.valueOf(text));
    } catch (NumberFormatException _) {
      return Optional.empty();
    }
  }

  /** An immutable copy that keeps the iteration order of {@code source}. */
  static <K, V> Map<K, V> orderedCopy(Map<K, V> source) {
    return Collections.unmodifiableSequencedMap(new LinkedHashMap<>(source));
  }

  /** A new insertion-ordered map with the same keys and each value transformed. */
  static <K, V> Map<K, Object> mapValues(Map<K, V> source, Function<? super V, ?> transform) {
    return source.entrySet().stream()
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                entry -> (Object) transform.apply(entry.getValue()),
                (_, latest) -> latest,
                LinkedHashMap::new));
  }

  /** One meaningful line of an output that is divided into {@code @section} blocks. */
  sealed interface SectionEntry {

    /** The line {@code @name}, which starts a section. */
    record Header(String name) implements SectionEntry {}

    /**
     * A line inside a section.
     *
     * @param section the section the line belongs to; empty before the first header
     * @param text the line without the device marker
     * @param raw the line exactly as printed
     */
    record Body(String section, String text, String raw) implements SectionEntry {}
  }

  /** A {@code key:type:value} line. */
  record PropertyLine(String key, String type, String value) {}
}
