package com.jbraddockm.core.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.testing.Resources;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Json#write(Object)} and {@link Json#object(Object...)}: serialization of every
 * supported value, escaping, and round trips.
 */
@DisplayName("Json.write and Json.object: writing JSON")
class JsonWriteTest {

  private static String expected(String resource) {
    return Resources.text(JsonWriteTest.class, resource).strip();
  }

  @Nested
  @DisplayName("what is written")
  class Writing {

    @Test
    @DisplayName(
        "each kind of value, in the compact form: text, numbers, flags, lists, sets, arrays of"
            + " objects and of primitives, optionals, maps, and what has no JSON of its own by its"
            + " text; a member that is null or an empty optional is left out, and null that is"
            + " meant is written")
    void everyKindOfValue() {
      var values = new LinkedHashMap<String, Object>();
      values.put("string", "s");
      values.put("char", 'c');
      values.put("flag", true);
      values.put("integer", 1);
      values.put("long", 2L);
      values.put("double", 1.5);
      values.put("float", 2.5f);
      values.put("big", new BigDecimal("1.50"));
      values.put("nan", Double.NaN);
      values.put("infinity", Double.POSITIVE_INFINITY);
      values.put("floatNan", Float.NaN);
      values.put("list", List.of(1, 2));
      values.put("set", new LinkedHashSet<>(List.of("x", "y")));
      values.put("array", new Object[] {"a", null});
      values.put("ints", new int[] {1, 2});
      values.put("flags", new boolean[] {true, false});
      values.put("doubles", new double[] {1.5, Double.NaN});
      values.put("chars", new char[] {'a', 'b'});
      values.put("present", Optional.of("x"));
      values.put("missing", Optional.empty());
      values.put("optionals", Arrays.asList(Optional.of("x"), Optional.empty()));
      values.put("nested", Json.object("k", "v"));
      values.put("explicitNull", Json.NULL);
      values.put("absent", null);
      values.put("enum", LogLevel.WARN);
      values.put("builder", new StringBuilder("text"));

      assertThat(Json.write(values)).isEqualTo(expected("json-writer--types.txt"));
    }

    @Test
    @DisplayName(
        "the characters that JSON cannot hold are escaped: the quote, the backslash, the controls,"
            + " and the others below a space as four hexadecimal digits in small letters")
    void escapes() {
      var text = "tab\tquote\"back\\slash\nnl\rcr\bbs\ffeed\u0001ctl\u001f";

      assertThat(Json.write(Json.object("text", text)))
          .isEqualTo(expected("json-writer--escapes.txt"));
    }

    @Test
    @DisplayName("the characters above 127 are written as they are, and so is the slash")
    void charactersAboveTheAsciiAreNotEscaped() {
      assertThat(Json.write("é😀/")).isEqualTo(expected("json-writer--non-ascii.txt"));
    }

    @Test
    @DisplayName(
        "null, and the null that is meant, are written as null; an empty list and an empty map as"
            + " [] and {}")
    void theSmallest() {
      assertThat(Json.write(null)).isEqualTo("null");
      assertThat(Json.write(Json.NULL)).isEqualTo("null");
      assertThat(Json.write(List.of())).isEqualTo("[]");
      assertThat(Json.write(Map.of())).isEqualTo("{}");
    }

    @Test
    @DisplayName(
        "a map is written in the order of its members, and a key that is not text is written as its"
            + " text")
    void keysAndTheirOrder() {
      var map = new LinkedHashMap<Object, Object>();
      map.put("b", 1);
      map.put(5, 2);
      map.put("a", 3);

      assertThat(Json.write(map)).isEqualTo(expected("json-writer--keys.txt"));
    }

    @Test
    @DisplayName(
        "a map that has a null among its members writes the others: a null is left out of an object"
            + " and kept in an array")
    void nullsInObjectsAndArrays() {
      var map = new HashMap<String, Object>();
      map.put("gone", null);

      assertThat(Json.write(map)).isEqualTo("{}");
      assertThat(Json.write(Arrays.asList(1, null, 3)))
          .isEqualTo(expected("json-writer--array-with-null.txt"));
    }
  }

  @Nested
  @DisplayName("what is written is read back as it was")
  class RoundTrip {

    @Test
    @DisplayName(
        "a document of every kind of value, read, written and read again, is the document that was"
            + " read the first time")
    void aDocumentSurvives() {
      var document = Json.parse(Resources.text(JsonWriteTest.class, "json--round-trip.json"));

      assertThat(Json.parse(Json.write(document))).isEqualTo(document);
    }

    @Test
    @DisplayName("every character below a space, and some above it, survive being written and read")
    void charactersSurvive() {
      var every = new StringBuilder();
      for (var code = 0; code < 0x80; code++) {
        every.append((char) code);
      }
      every.append("é😀\u2028");

      assertThat(Json.parse(Json.write(every.toString()))).isEqualTo(every.toString());
    }
  }

  @Nested
  @DisplayName("Json.object")
  class MakingObjects {

    @Test
    @DisplayName(
        "makes a map of the pairs that it is given, in order, and keeps a value that is null")
    void makesAMap() {
      var object = Json.object("b", 1, "a", null);

      assertThat(List.copyOf(object.keySet())).containsExactly("b", "a");
      assertThat(object).containsEntry("b", 1).containsEntry("a", null);
    }

    @Test
    @DisplayName("makes an empty map of no pairs, and a map that can be added to")
    void makesAnEmptyMap() {
      var object = Json.object();

      assertThat(object).isEmpty();
      object.put("later", 1);
      assertThat(object).containsEntry("later", 1);
    }

    @Test
    @DisplayName("keeps the last value when a key comes twice")
    void theLastValueWins() {
      assertThat(Json.object("a", 1, "a", 2)).containsEntry("a", 2).hasSize(1);
    }

    @Test
    @DisplayName("refuses a key without a value")
    void refusesAnOddNumber() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Json.object("a", 1, "b"))
          .withMessage("keys and values must come in pairs");
    }

    @Test
    @DisplayName("refuses a key that is not text, saying where it is")
    void refusesAKeyThatIsNotText() {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Json.object("a", 1, 2, 3))
          .withMessage("key at position 2 is not a string");
    }
  }
}
