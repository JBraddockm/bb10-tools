package com.jbraddockm.core.json;

import com.jbraddockm.core.json.Json.JsonException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A recursive-descent JSON parser producing plain Java values.
 *
 * <p>Numbers and escapes follow the JSON grammar. A whole number is a {@code Long} and one with a
 * fraction or an exponent a {@code Double}; a number that does not fit its type is refused.
 */
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
      var code = HexFormat.fromHexDigits(text, position, position + 4);
      position += 4;
      return (char) code;
    } catch (NumberFormatException _) {
      throw error("bad unicode escape");
    }
  }

  /** Reads {@code -? (0 | [1-9][0-9]*) (. [0-9]+)? ([eE] [+-]? [0-9]+)?}. */
  private Object readNumber() {
    var start = position;
    var first = text.charAt(position);
    if (first != '-' && !isDigit(first)) {
      throw error("unexpected character '" + first + "'");
    }
    accept('-');
    var wellFormed = integerPart();
    var whole = true;
    if (wellFormed && accept('.')) {
      whole = false;
      wellFormed = digits();
    }
    if (wellFormed && (accept('e') || accept('E'))) {
      whole = false;
      if (!accept('+')) {
        accept('-');
      }
      wellFormed = digits();
    }
    if (wellFormed) {
      var literal = text.substring(start, position);
      try {
        if (whole) {
          return Long.valueOf(literal);
        }
        var number = Double.valueOf(literal);
        if (Double.isFinite(number)) {
          return number;
        }
      } catch (NumberFormatException _) {
        // a whole number that does not fit a long is refused below
      }
    }
    position = start;
    throw error("bad number");
  }

  /** Reads {@code 0}, or a digit from 1 to 9 followed by any digits. */
  private boolean integerPart() {
    if (accept('0')) {
      return true;
    }
    return position < text.length() && isDigit(text.charAt(position)) && digits();
  }

  /** Reads one or more digits. */
  private boolean digits() {
    var start = position;
    while (position < text.length() && isDigit(text.charAt(position))) {
      position++;
    }
    return position > start;
  }

  private boolean accept(char expected) {
    if (position < text.length() && text.charAt(position) == expected) {
      position++;
      return true;
    }
    return false;
  }

  private static boolean isDigit(char character) {
    return character >= '0' && character <= '9';
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
