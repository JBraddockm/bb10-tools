package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link BarKind}, {@link BarInfo}, {@link BarValidation} and {@link
 * InvalidBarException}.
 */
@DisplayName("Bar types: BarKind, BarInfo, BarValidation and InvalidBarException")
class BarTypesTest {

  @Nested
  @DisplayName("BarKind")
  class Kind {

    @ParameterizedTest(name = "{0} is \"{1}\", and is firmware: {2}")
    @CsvSource({
      "APPLICATION, application, false",
      "OPERATING_SYSTEM, operatingSystem, true",
      "RADIO, radio, true",
      "OTHER, other, false"
    })
    @DisplayName(
        "each kind has the word that the protocol uses, and the operating system and the radio are"
            + " firmware and nothing else is")
    void eachKind(BarKind kind, String wireName, boolean firmware) {
      assertThat(kind.wireName()).isEqualTo(wireName);
      assertThat(kind.isFirmware()).isEqualTo(firmware);
    }
  }

  @Nested
  @DisplayName("BarInfo")
  class Info {

    private BarInfo info(String id) {
      return new BarInfo(
          Path.of("app.bar"),
          421L,
          BarKind.APPLICATION,
          "application",
          id,
          null,
          null,
          null,
          null,
          false,
          false);
    }

    @Test
    @DisplayName("needs a file, a kind and a package type")
    void needsFileKindAndType() {
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new BarInfo(
                      null, 1, BarKind.OTHER, "x", null, null, null, null, null, false, false))
          .withMessage("file");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new BarInfo(
                      Path.of("a"), 1, null, "x", null, null, null, null, null, false, false))
          .withMessage("kind");
      assertThatNullPointerException()
          .isThrownBy(
              () ->
                  new BarInfo(
                      Path.of("a"),
                      1,
                      BarKind.OTHER,
                      null,
                      null,
                      null,
                      null,
                      null,
                      null,
                      false,
                      false))
          .withMessage("packageType");
    }

    @Test
    @DisplayName("leaves out of its JSON what the manifest did not say")
    void leavesOutWhatIsMissing() {
      var written = Json.write(info(null).toJson());

      assertThat(written)
          .doesNotContain(
              "packageId", "packageName", "packageVersion", "architecture", "manifestVersion");
      assertThat(written)
          .contains(
              "\"file\":\"app.bar\"",
              "\"sizeBytes\":421",
              "\"kind\":\"application\"",
              "\"developmentMode\":false",
              "\"signed\":false");
    }

    @Test
    @DisplayName("says what the manifest did say")
    void saysWhatIsThere() {
      assertThat(info("idFirst").toJson()).containsEntry("packageId", "idFirst");
    }
  }

  @Nested
  @DisplayName("BarValidation")
  class Validation {

    private final BarValidation.Message warning =
        new BarValidation.Message(BarValidation.Severity.WARNING, "hmm");
    private final BarValidation.Message error =
        new BarValidation.Message(BarValidation.Severity.ERROR, "no");

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"ERROR, error", "WARNING, warning"})
    @DisplayName("each severity is written in small letters")
    void eachSeverity(BarValidation.Severity severity, String wireName) {
      assertThat(severity.wireName()).isEqualTo(wireName);
    }

    @Test
    @DisplayName(
        "is valid with no finding, and with only warnings, and is not valid with an error among"
            + " them")
    void validity() {
      assertThat(new BarValidation("unsigned", List.of()).valid()).isTrue();
      assertThat(new BarValidation("unsigned", List.of(warning)).valid()).isTrue();
      assertThat(new BarValidation("unsigned", List.of(warning, error)).valid()).isFalse();
    }

    @Test
    @DisplayName(
        "lists each finding with its severity and its text, in order, in its JSON, with the rules"
            + " and the verdict")
    void asJson() {
      var json = new BarValidation("signed", List.of(warning, error)).toJson();

      assertThat(List.copyOf(json.keySet())).containsExactly("rules", "valid", "messages");
      assertThat(json).containsEntry("rules", "signed").containsEntry("valid", false);
      assertThat(Json.write(json))
          .contains(
              "[{\"severity\":\"warning\",\"text\":\"hmm\"},{\"severity\":\"error\",\"text\":\"no\"}]");
    }

    @Test
    @DisplayName("keeps its own copy of the findings, which nobody can change")
    void keepsItsOwnCopy() {
      var given = new ArrayList<>(List.of(warning));
      var validation = new BarValidation("unsigned", given);

      given.add(error);

      assertThat(validation.messages()).containsExactly(warning);
      assertThatThrownBy(() -> validation.messages().add(error))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("needs the rules, and a finding needs a severity and a text")
    void needsTheParts() {
      assertThatNullPointerException()
          .isThrownBy(() -> new BarValidation(null, List.of()))
          .withMessage("rules");
      assertThatNullPointerException()
          .isThrownBy(() -> new BarValidation.Message(null, "x"))
          .withMessage("severity");
      assertThatNullPointerException()
          .isThrownBy(() -> new BarValidation.Message(BarValidation.Severity.ERROR, null))
          .withMessage("text");
    }
  }

  @Test
  @DisplayName(
      "InvalidBarException says why a file is not a usable bar, and is checked, so that a caller"
          + " cannot forget it")
  void theRefusal() {
    var exception = new InvalidBarException("it is a folder, not a file");

    assertThat(exception)
        .hasMessage("it is a folder, not a file")
        .isInstanceOf(Exception.class)
        .isNotInstanceOf(RuntimeException.class);
  }
}
