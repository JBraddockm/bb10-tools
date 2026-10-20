package com.jbraddockm.core.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.testing.JsonObjects;
import com.jbraddockm.core.testing.Resources;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;

/**
 * Tests for {@link Json#parse(String)}: accepted documents, their Java representation, and the
 * messages for rejected ones.
 */
@DisplayName("Json.parse: reading JSON")
class JsonParseTest {

  private static String text(String resource) {
    return Resources.text(JsonParseTest.class, resource);
  }

  @Nested
  @DisplayName("what is read")
  class Reading {

    @ParameterizedTest(name = "{0} is written back as {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/core/json/json--valid.csv",
        delimiter = '|',
        quoteCharacter = '`')
    @DisplayName(
        "a valid text is read, and is written back in the compact form, without the members that"
            + " are null")
    void aValidText(String text, String written) {
      assertThat(Json.write(Json.parse(text))).isEqualTo(written);
    }

    @Test
    @DisplayName(
        "a whole number is a Long, a number with a fraction or an exponent is a Double, whatever"
            + " its size")
    void numbersAreLongOrDouble() {
      assertThat(Json.parse("12")).isInstanceOf(Long.class).isEqualTo(12L);
      assertThat(Json.parse("4294967296")).isInstanceOf(Long.class).isEqualTo(4_294_967_296L);
      assertThat(Json.parse("1.0")).isInstanceOf(Double.class).isEqualTo(1.0);
      assertThat(Json.parse("1e2")).isInstanceOf(Double.class).isEqualTo(100.0);
    }

    @Test
    @DisplayName("null, true and false are null and the two booleans")
    void literals() {
      assertThat(Json.parse("null")).isNull();
      assertThat(Json.parse("true")).isEqualTo(Boolean.TRUE);
      assertThat(Json.parse("false")).isEqualTo(Boolean.FALSE);
    }

    @Test
    @DisplayName(
        "an object is read into a map that keeps the order of its members, and an array into a"
            + " list")
    void objectsAndArrays() {
      var object = Json.parse(text("json--object-and-array.json"));

      assertThat(List.copyOf(JsonObjects.object(object).keySet())).containsExactly("b", "a");
      assertThat(JsonObjects.object(object).get("a"))
          .isInstanceOf(List.class)
          .isEqualTo(Arrays.asList(true, null));
    }

    @Test
    @DisplayName("when a key comes twice the last member is the one that is kept")
    void theLastKeyWins() {
      assertThat(JsonObjects.object(Json.parse(text("json--duplicate-key.json"))))
          .containsEntry("a", 2L)
          .hasSize(1);
    }

    @Test
    @DisplayName("an escape of a pair of surrogates is read as the one character that it makes")
    void surrogates() {
      var character = (String) Json.parse(text("json--surrogate-pair.json"));

      assertThat(character).isEqualTo("\uD83D\uDE00");
      assertThat(character.codePointCount(0, character.length())).isEqualTo(1);
    }

    @Test
    @DisplayName("blanks around a text, of every kind that JSON allows, are of no account")
    void blanksAroundAreIgnored() {
      assertThat(Json.parse(text("json--padded.json"))).isEqualTo(List.of(1L, 2L));
    }

    @Test
    @DisplayName("arrays nested 65 deep are read")
    void deepEnough() {
      assertThat(Json.parse(text("json--nested-65.json"))).isInstanceOf(List.class);
    }
  }

  @Nested
  @DisplayName("what is refused")
  class Refusing {

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(
        resources = "/com/jbraddockm/core/json/json--invalid.csv",
        delimiter = '|',
        quoteCharacter = '`')
    @DisplayName("a text that is not JSON is refused with what is wrong and where")
    void aTextThatIsNotJson(String invalid, String message) {
      assertThatThrownBy(() -> Json.parse(invalid))
          .isInstanceOf(Json.JsonException.class)
          .hasMessage(message);
    }

    @Test
    @DisplayName(
        "an empty text is refused, and so is one of blanks, which ends where the blanks end")
    void nothingToRead() {
      assertThatThrownBy(() -> Json.parse(text("json--empty.txt")))
          .isInstanceOf(Json.JsonException.class)
          .hasMessage("unexpected end of input at position 0");
      assertThatThrownBy(() -> Json.parse(text("json--only-blanks.txt")))
          .isInstanceOf(Json.JsonException.class)
          .hasMessage("unexpected end of input at position 3");
    }

    @Test
    @DisplayName("a control character inside a string is refused, where it is")
    void aControlCharacter() {
      assertThatThrownBy(() -> Json.parse(text("json--tab-in-string.json")))
          .isInstanceOf(Json.JsonException.class)
          .hasMessage("control character in string at position 2");
    }

    @Test
    @DisplayName(
        "arrays nested 66 deep are refused, as too deep, at the one that is too deep: this keeps a"
            + " hostile request from overflowing the stack")
    void tooDeep() {
      assertThatThrownBy(() -> Json.parse(text("json--nested-66.json")))
          .isInstanceOf(Json.JsonException.class)
          .hasMessage("nesting too deep at position 65");
    }
  }
}
