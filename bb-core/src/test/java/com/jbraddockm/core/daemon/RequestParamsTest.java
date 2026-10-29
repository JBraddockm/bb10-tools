package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import java.nio.file.Path;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link RequestParams}: typed readers for request parameters and their validation
 * errors.
 */
@DisplayName("RequestParams: the parameters of a request")
class RequestParamsTest {

  private final RequestParams params =
      new RequestParams(
          (Map<?, ?>) Json.parse(Resources.text(RequestParamsTest.class, "params--all.json")));

  private static void assertRefused(Runnable read, String name, String reason) {
    assertThatThrownBy(read::run)
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo(name))
        .hasMessage("'" + name + "' " + reason);
  }

  @Test
  @DisplayName("names are those of the members, in the order they were written")
  void names() {
    assertThat(params.names())
        .startsWith("object", "text", "blank", "number")
        .contains("second")
        .hasSize(18);
  }

  @Nested
  @DisplayName("an object")
  class Objects {

    @Test
    @DisplayName("is read as parameters of its own, whose members are read the same way")
    void anObject() {
      var inner = params.requireObject("object");

      assertThat(inner.requireString("inner")).isEqualTo("x");
      assertThat(inner.flag("flag")).isTrue();
    }

    @Test
    @DisplayName("is absent when there is none, or when it is null")
    void absent() {
      assertThat(params.optionalObject("missing")).isEmpty();
      assertThat(params.optionalObject("nullMember")).isEmpty();
    }

    @Test
    @DisplayName(
        "that is required and not there is refused, and so is a member that is not an object")
    void refusals() {
      assertRefused(() -> params.requireObject("missing"), "missing", "is required");
      assertRefused(() -> params.optionalObject("text"), "text", "must be an object");
    }

    @Test
    @DisplayName("is read as a map of its members for a command that forwards it verbatim")
    void asAMap() {
      assertThat(params.requireObjectMap("object"))
          .containsEntry("inner", "x")
          .containsEntry("flag", true);
    }

    @Test
    @DisplayName("as a map, is refused when absent or not an object")
    void asAMapRefusals() {
      assertRefused(() -> params.requireObjectMap("missing"), "missing", "is required");
      assertRefused(() -> params.requireObjectMap("text"), "text", "must be an object");
    }
  }

  @Nested
  @DisplayName("a text")
  class Texts {

    @Test
    @DisplayName(
        "is read as it is, blanks included, when it is optional, and is absent when there is none")
    void optional() {
      assertThat(params.optionalString("text")).contains("hello");
      assertThat(params.optionalString("blank")).contains("   ");
      assertThat(params.optionalString("missing")).isEmpty();
      assertThat(params.optionalString("nullMember")).isEmpty();
    }

    @Test
    @DisplayName("that is required is read, and is refused when it is absent or blank")
    void required() {
      assertThat(params.requireString("text")).isEqualTo("hello");
      assertRefused(() -> params.requireString("missing"), "missing", "is required");
      assertRefused(() -> params.requireString("blank"), "blank", "is required");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"number", "yes", "list", "object"})
    @DisplayName("is refused when the member is of another kind")
    void ofAnotherKind(String name) {
      assertRefused(() -> params.optionalString(name), name, "must be a string");
      assertRefused(() -> params.requireString(name), name, "must be a string");
    }
  }

  @Nested
  @DisplayName("a path")
  class Paths {

    @Test
    @DisplayName("is read from a text, and is absent when there is none")
    void aPath() {
      assertThat(params.optionalPath("path")).contains(Path.of("folder/file.bar"));
      assertThat(params.requirePath("path")).isEqualTo(Path.of("folder/file.bar"));
      assertThat(params.optionalPath("missing")).isEmpty();
    }

    @Test
    @DisplayName(
        "is refused when it is not a valid path, when it is required and blank or absent, and when"
            + " it is not a text")
    void refusals() {
      assertRefused(() -> params.optionalPath("badPath"), "badPath", "is not a valid path");
      assertRefused(() -> params.requirePath("blank"), "blank", "is required");
      assertRefused(() -> params.requirePath("missing"), "missing", "is required");
      assertRefused(() -> params.optionalPath("number"), "number", "must be a string");
    }
  }

  @Nested
  @DisplayName("a list of texts")
  class Lists {

    @Test
    @DisplayName("is read in order, and is empty when there is none or when it is null")
    void aList() {
      assertThat(params.optionalStrings("list")).containsExactly("a", "b");
      assertThat(params.optionalStrings("missing")).isEmpty();
      assertThat(params.optionalStrings("nullMember")).isEmpty();
      assertThat(params.requireStrings("list")).containsExactly("a", "b");
    }

    @Test
    @DisplayName("is a copy that nobody can change")
    void aCopy() {
      assertThatThrownBy(() -> params.optionalStrings("list").add("c"))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"mixedList", "blankItemList"})
    @DisplayName("is refused when an item is not a text or is blank")
    void badItems(String name) {
      assertRefused(
          () -> params.optionalStrings(name), name, "must contain only non-empty strings");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text", "number", "object"})
    @DisplayName("is refused when the member is not an array")
    void notAnArray(String name) {
      assertRefused(() -> params.optionalStrings(name), name, "must be an array of strings");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"emptyList", "missing", "nullMember"})
    @DisplayName("that is required is refused when it is empty or absent")
    void requiredAndEmpty(String name) {
      assertRefused(() -> params.requireStrings(name), name, "is required and must not be empty");
    }
  }

  @Nested
  @DisplayName("a flag")
  class Flags {

    @Test
    @DisplayName("is what it says, and is false when it is absent")
    void aFlag() {
      assertThat(params.flag("yes")).isTrue();
      assertThat(params.flag("no")).isFalse();
      assertThat(params.flag("missing")).isFalse();
    }

    @Test
    @DisplayName(
        "takes the fallback when it is absent or null, and what it says wins over the fallback")
    void theFallback() {
      assertThat(params.flag("missing", true)).isTrue();
      assertThat(params.flag("nullMember", true)).isTrue();
      assertThat(params.flag("no", true)).isFalse();
      assertThat(params.flag("yes", false)).isTrue();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"text", "number", "second"})
    @DisplayName("is refused when it is not a boolean: a text such as yes, or 1, is not one")
    void notABoolean(String name) {
      assertRefused(() -> params.flag(name), name, "must be a boolean");
    }
  }

  @Nested
  @DisplayName("a whole number")
  class Integers {

    @Test
    @DisplayName("is read within its bounds, the bounds included, and is absent when there is none")
    void withinBounds() {
      assertThat(params.optionalInteger("number", 1, 10)).isEqualTo(OptionalInt.of(5));
      assertThat(params.optionalInteger("number", 5, 5)).isEqualTo(OptionalInt.of(5));
      assertThat(params.optionalInteger("zero", 0, 10)).isEqualTo(OptionalInt.of(0));
      assertThat(params.optionalInteger("negative", -3, 0)).isEqualTo(OptionalInt.of(-3));
      assertThat(params.optionalInteger("missing", 1, 10)).isEmpty();
      assertThat(params.optionalInteger("nullMember", 1, 10)).isEmpty();
    }

    @Test
    @DisplayName("with a default is that, when it is absent, and what it says otherwise")
    void withADefault() {
      assertThat(params.integer("missing", 42, 1, 100)).isEqualTo(42);
      assertThat(params.integer("number", 42, 1, 100)).isEqualTo(5);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"number, 6, 10", "number, 1, 4", "big, 1, 10", "negative, 0, 10", "zero, 1, 10"})
    @DisplayName("is refused when it is outside its bounds, saying what they are")
    void outsideTheBounds(String name, int minimum, int maximum) {
      var reason = "must be between " + minimum + " and " + maximum;

      assertRefused(() -> params.optionalInteger(name, minimum, maximum), name, reason);
      assertRefused(() -> params.integer(name, minimum, minimum, maximum), name, reason);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"fraction", "text", "yes", "list", "second"})
    @DisplayName("is refused when it is not an integer: a fraction, a text or a flag")
    void notAnInteger(String name) {
      assertRefused(() -> params.optionalInteger(name, 0, 10), name, "must be an integer");
      assertRefused(() -> params.integer(name, 1, 0, 10), name, "must be an integer");
    }
  }
}
