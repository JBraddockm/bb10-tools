package com.jbraddockm.core.json;

import java.io.Serial;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A minimal JSON reader and writer working on plain Java values.
 *
 * <p>JSON objects are {@link Map maps}, arrays are {@link java.util.List lists}, numbers are {@link
 * Long} or {@link Double}, and {@code null} is Java {@code null}. The implementation uses no
 * reflection, so it needs no native-image configuration.
 */
public final class Json {

  /**
   * Writes as JSON {@code null}. A plain Java {@code null} used as a map value is <em>omitted</em>
   * from the output instead, which is how optional fields are expressed.
   */
  public static final Object NULL = Null.INSTANCE;

  private Json() {}

  /**
   * Builds an insertion-ordered JSON object from alternating keys and values.
   *
   * @param keysAndValues alternating {@code String} keys and values; values that are {@code null}
   *     are omitted when the object is written
   * @return a mutable, insertion-ordered map
   * @throws IllegalArgumentException if the arguments do not come in pairs or a key is not a string
   */
  public static Map<String, Object> object(Object... keysAndValues) {
    if (keysAndValues.length % 2 != 0) {
      throw new IllegalArgumentException("keys and values must come in pairs");
    }
    var object = new LinkedHashMap<String, Object>();
    for (int index = 0; index < keysAndValues.length; index += 2) {
      if (!(keysAndValues[index] instanceof String key)) {
        throw new IllegalArgumentException("key at position " + index + " is not a string");
      }
      object.put(key, keysAndValues[index + 1]);
    }
    return object;
  }

  /**
   * Serialises a value to compact JSON.
   *
   * @param value a map, list, array (of objects or of primitives), optional, string, number,
   *     boolean, {@link #NULL} or {@code null}; a member that is {@code null} or an empty optional
   *     is left out of an object
   * @return the JSON text
   */
  public static String write(Object value) {
    return JsonWriter.write(value);
  }

  /**
   * Parses JSON text.
   *
   * @param text the complete JSON document
   * @return the parsed value; {@code null} for the JSON literal {@code null}
   * @throws JsonException if the text is not valid JSON
   */
  public static Object parse(String text) {
    return JsonReader.parse(text);
  }

  /** Marker for values that are written as JSON {@code null}. */
  public enum Null {
    /** The only instance. */
    INSTANCE
  }

  /** Signals malformed JSON input. */
  public static final class JsonException extends RuntimeException {
    @Serial private static final long serialVersionUID = 1L;

    JsonException(String message) {
      super(message);
    }
  }
}
