package com.jbraddockm.internal;

import com.jbraddockm.internal.Json.JsonException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A recursive-descent JSON parser producing plain Java values. */
final class JsonReader {

  private static final int MAX_DEPTH = 64;

  private final String text;
  private int position;

  private JsonReader(String text) {
    this.text = text;
  }

  static Object parse(String text) {
    var reader = new JsonReader(text);
    reader.skipWhitespace();
    var value = reader.readValue(0);
    reader.skipWhitespace();
    if (reader.position != text.length()) {
      throw reader.error("unexpected trailing characters");
    }
    return value;
  }

  private JsonException error(String message) {
    return new JsonException(message + " at position " + position);
  }

  private void skipWhitespace() {
    while (position < text.length() && " \t\n\r".indexOf(text.charAt(position)) >= 0) {
      position++;
    }
  }

  private Object readValue(int depth) {
    if (depth > MAX_DEPTH) {
      throw error("nesting too deep");
    }
    if (position >= text.length()) {
      throw error("unexpected end of input");
    }
    return switch (text.charAt(position)) {
      case '{' -> readObject(depth);
      case '[' -> readArray(depth);
      case '"' -> readString();
      case 't' -> readLiteral("true", Boolean.TRUE);
      case 'f' -> readLiteral("false", Boolean.FALSE);
      case 'n' -> readLiteral("null", null);
      default -> readNumber();
    };
  }

  private Object readLiteral(String word, Object value) {
    if (!text.startsWith(word, position)) {
      throw error("unexpected token");
    }
    position += word.length();
    return value;
  }

  private Map<String, Object> readObject(int depth) {
    position++; // opening brace
    var object = new LinkedHashMap<String, Object>();
    skipWhitespace();
    if (peek() == '}') {
      position++;
      return object;
    }
    while (true) {
      skipWhitespace();
      if (peek() != '"') {
        throw error("expected string key");
      }
      var key = readString();
      skipWhitespace();
      if (next() != ':') {
        position--;
        throw error("expected ':'");
      }
      skipWhitespace();
      object.put(key, readValue(depth + 1));
      skipWhitespace();
      switch (next()) {
        case '}' -> {
          return object;
        }
        case ',' -> {
          // continue with the next member
        }
        default -> {
          position--;
          throw error("expected ',' or '}'");
        }
      }
    }
  }

  private List<Object> readArray(int depth) {
    position++; // opening bracket
    var array = new ArrayList<Object>();
    skipWhitespace();
    if (peek() == ']') {
      position++;
      return array;
    }
    while (true) {
      skipWhitespace();
      array.add(readValue(depth + 1));
      skipWhitespace();
      switch (next()) {
        case ']' -> {
          return array;
        }
        case ',' -> {
          // continue with the next element
        }
        default -> {
          position--;
          throw error("expected ',' or ']'");
        }
      }
    }
  }

  private String readString() {
    position++; // opening quote
    var builder = new StringBuilder();
    while (true) {
      var character = next();
      if (character == '"') {
        return builder.toString();
      }
      if (character < 0x20) {
        position--;
        throw error("control character in string");
      }
      if (character == '\\') {
        builder.append(readEscape());
      } else {
        builder.append(character);
      }
    }
  }

  private char readEscape() {
    return switch (next()) {
      case '"' -> '"';
      case '\\' -> '\\';
      case '/' -> '/';
      case 'b' -> '\b';
      case 'f' -> '\f';
      case 'n' -> '\n';
      case 'r' -> '\r';
      case 't' -> '\t';
      case 'u' -> readUnicodeEscape();
      default -> {
        position--;
        throw error("bad escape");
      }
    };
  }

  private char readUnicodeEscape() {
    if (position + 4 > text.length()) {
      throw error("bad unicode escape");
    }
    try {
      var code = Integer.parseInt(text.substring(position, position + 4), 16);
      position += 4;
      return (char) code;
    } catch (NumberFormatException _) {
      throw error("bad unicode escape");
    }
  }

  private Object readNumber() {
    var start = position;
    while (position < text.length() && "+-0123456789.eE".indexOf(text.charAt(position)) >= 0) {
      position++;
    }
    if (start == position) {
      throw error("unexpected character '" + text.charAt(position) + "'");
    }
    var literal = text.substring(start, position);
    try {
      return literal.contains(".") || literal.contains("e") || literal.contains("E")
          ? (Object) Double.valueOf(literal)
          : (Object) Long.valueOf(literal);
    } catch (NumberFormatException _) {
      position = start;
      throw error("bad number");
    }
  }

  private char peek() {
    if (position >= text.length()) {
      throw error("unexpected end of input");
    }
    return text.charAt(position);
  }

  private char next() {
    var character = peek();
    position++;
    return character;
  }
}
