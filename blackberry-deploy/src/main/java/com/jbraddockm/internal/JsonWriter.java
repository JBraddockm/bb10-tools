package com.jbraddockm.internal;

import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/** Serialises plain Java values to compact JSON. */
final class JsonWriter {

  private JsonWriter() {}

  static String write(Object value) {
    return switch (value) {
      case null -> "null";
      case Json.Null _ -> "null";
      case CharSequence text -> quote(text);
      case Boolean flag -> flag.toString();
      case Double number when !Double.isFinite(number) -> "null";
      case Float number when !Float.isFinite(number) -> "null";
      case Number number -> number.toString();
      case java.util.Map<?, ?> map -> writeObject(map);
      case Iterable<?> items -> writeArray(StreamSupport.stream(items.spliterator(), false));
      case Object[] items -> writeArray(Arrays.stream(items));
      default -> quote(value.toString());
    };
  }

  private static String writeObject(java.util.Map<?, ?> map) {
    return map.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(entry -> quote(String.valueOf(entry.getKey())) + ':' + write(entry.getValue()))
        .collect(Collectors.joining(",", "{", "}"));
  }

  private static String writeArray(Stream<?> items) {
    return items.map(JsonWriter::write).collect(Collectors.joining(",", "[", "]"));
  }

  private static String quote(CharSequence text) {
    var builder = new StringBuilder(text.length() + 2).append('"');
    text.chars().forEach(code -> appendEscaped(builder, (char) code));
    return builder.append('"').toString();
  }

  private static void appendEscaped(StringBuilder builder, char character) {
    switch (character) {
      case '"' -> builder.append("\\\"");
      case '\\' -> builder.append("\\\\");
      case '\n' -> builder.append("\\n");
      case '\r' -> builder.append("\\r");
      case '\t' -> builder.append("\\t");
      case '\b' -> builder.append("\\b");
      case '\f' -> builder.append("\\f");
      default -> {
        if (character < 0x20) {
          builder.append(String.format("\\u%04x", (int) character));
        } else {
          builder.append(character);
        }
      }
    }
  }
}
