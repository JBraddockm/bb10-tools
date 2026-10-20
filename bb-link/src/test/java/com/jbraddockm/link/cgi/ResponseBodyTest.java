package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ResponseBody}: XML becomes JSON, JSON is kept as a tree, and anything else stays
 * text.
 */
@DisplayName("ResponseBody: what the body of an answer is")
class ResponseBodyTest {

  private static String resource(String name) {
    return Resources.text(ResponseBodyTest.class, name);
  }

  @Nested
  @DisplayName("XML")
  class Xml {

    @Test
    @DisplayName("is recognised by its content, and becomes the JSON that the mapping gives")
    void isRecognisedAndConverted() {
      var body = ResponseBody.of(resource("response-body--xml.xml"));

      assertThat(body.format()).isEqualTo(BodyFormat.XML);
      assertThat(Json.write(body.value())).isEqualTo(resource("response-body--xml.json").strip());
    }
  }

  @Nested
  @DisplayName("JSON")
  class JsonBodies {

    @Test
    @DisplayName(
        "an object is recognised by its content, even after blanks, and is the tree itself")
    void anObjectIsRecognised() {
      var body = ResponseBody.of(resource("response-body--json-object.txt"));

      assertThat(body.format()).isEqualTo(BodyFormat.JSON);
      assertThat(Json.write(body.value()))
          .isEqualTo(resource("response-body--json-object.json").strip());
    }

    @Test
    @DisplayName("an array is recognised too")
    void anArrayIsRecognised() {
      var body = ResponseBody.of(resource("response-body--json-array.txt"));

      assertThat(body.format()).isEqualTo(BodyFormat.JSON);
      assertThat(Json.write(body.value()))
          .isEqualTo(resource("response-body--json-array.json").strip());
    }
  }

  @Nested
  @DisplayName("text")
  class Text {

    @ParameterizedTest(name = "{0} stays text")
    @ValueSource(
        strings = {
          "response-body--text--empty.txt",
          "response-body--text--plain.txt",
          "response-body--text--device-not-logged-in.txt",
          "response-body--text--unclosed-html.txt",
          "response-body--text--json-then-text.txt",
          "response-body--text--digits.txt",
          "response-body--text--xml-then-text.txt"
        })
    @DisplayName(
        "is what is left: it is kept as it came, unchanged, even if it only looked like XML or"
            + " JSON")
    void isKeptUnchanged(String resource) {
      var text = resource(resource);

      var body = ResponseBody.of(text);

      assertThat(body.format()).isEqualTo(BodyFormat.TEXT);
      assertThat(body.value()).isEqualTo(text);
    }
  }

  @Test
  @DisplayName("a body has a format and a value")
  void aBodyHasAFormatAndAValue() {
    assertThatNullPointerException()
        .isThrownBy(() -> new ResponseBody(null, "x"))
        .withMessage("format");
    assertThatNullPointerException()
        .isThrownBy(() -> new ResponseBody(BodyFormat.TEXT, null))
        .withMessage("value");
  }
}
