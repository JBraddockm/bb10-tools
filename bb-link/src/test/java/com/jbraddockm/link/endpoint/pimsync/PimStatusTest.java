package com.jbraddockm.link.endpoint.pimsync;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.Resources;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PimStatus}: reading the status from a JSON or text answer. */
@DisplayName("PimStatus: the status in the answer of pimsync")
class PimStatusTest {

  private static Optional<PimStatus> read(String resource) {
    return PimStatus.read(Resources.text(PimStatusTest.class, resource));
  }

  @Nested
  @DisplayName("an answer that is JSON")
  class Json {

    @Test
    @DisplayName("gives the code and the message of its status")
    void givesTheCodeAndTheMessage() {
      assertThat(read("pim--ok.json")).contains(new PimStatus(0, "OK"));
      assertThat(read("pim--invalid-parameters.json"))
          .contains(new PimStatus(1001, "invalid parameter workstation_id"));
    }

    @Test
    @DisplayName("gives a negative code as it is")
    void givesANegativeCode() {
      assertThat(read("pim--negative-code.json")).contains(new PimStatus(-1, "negative"));
    }

    @Test
    @DisplayName("gives no message when the status has none, or when it is not text")
    void givesNoMessage() {
      assertThat(read("pim--rejected-without-message.json")).contains(new PimStatus(1001, null));
      assertThat(read("pim--message-is-a-number.json")).contains(new PimStatus(5, null));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "pim--no-status.json",
          "pim--code-is-a-string.json",
          "pim--code-is-a-fraction.json",
          "pim--not-an-object.json"
        })
    @DisplayName(
        "gives no status when there is none: no status object, a code that is not a whole number,"
            + " or JSON that is not an object")
    void givesNoStatus(String resource) {
      assertThat(read(resource)).isEmpty();
    }
  }

  @Nested
  @DisplayName("an answer that is not JSON")
  class Text {

    @Test
    @DisplayName(
        "gives the status that the text says: what a real device answers when there is no session")
    void givesTheStatusOfTheText() {
      assertThat(read("pim--not-logged-in.txt"))
          .contains(new PimStatus(1013, "received HTTP 401/403 from server"));
    }

    @Test
    @DisplayName("gives a message with its escaped quotes and backslashes as they were meant")
    void unescapesTheMessage() {
      assertThat(read("pim--not-json-with-escapes.txt"))
          .hasValueSatisfying(
              status -> {
                assertThat(status.code()).isEqualTo(1001);
                assertThat(status.message()).isEqualTo("bad \"x\" value \\ end");
              });
    }

    @Test
    @DisplayName("gives no message when the text has a code and none")
    void givesNoMessage() {
      assertThat(read("pim--not-json-code-only.txt")).contains(new PimStatus(1001, null));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"pim--not-json-without-status.txt", "pim--empty.txt"})
    @DisplayName("gives no status when the text has none, or when there is no text")
    void givesNoStatus(String resource) {
      assertThat(read(resource)).isEmpty();
    }
  }
}
