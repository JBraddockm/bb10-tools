package com.jbraddockm.link.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the package and perimeter kinds of the device properties. */
@DisplayName("PackageType and PerimeterKind: the kinds that the properties name")
class PropertyKindsTest {

  @Nested
  @DisplayName("PackageType")
  class Package {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource(
        value = {
          "application, APPLICATION",
          "os, OPERATING_SYSTEM",
          "radio, RADIO",
          "'', UNCLASSIFIED",
          "weird, UNKNOWN",
          "?, UNKNOWN",
          "OS, UNKNOWN",
          "Radio, UNKNOWN"
        })
    @DisplayName(
        "of reads the word of the device exactly, an empty one as unclassified, and any other as"
            + " unknown")
    void ofReadsTheWordOfTheDevice(String text, PackageType expected) {
      assertThat(PackageType.of(text)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "APPLICATION, application",
      "OPERATING_SYSTEM, operatingSystem",
      "RADIO, radio",
      "UNCLASSIFIED, unclassified",
      "UNKNOWN, unknown"
    })
    @DisplayName("each kind has the name that the protocol of the daemon uses")
    void eachKindHasItsJsonName(PackageType type, String jsonName) {
      assertThat(type.jsonName()).isEqualTo(jsonName);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(PackageType.class)
    @DisplayName("has a distinct JSON name for each kind")
    void jsonNamesAreDistinct(PackageType type) {
      assertThat(
              Arrays.stream(PackageType.values())
                  .filter(other -> other.jsonName().equals(type.jsonName())))
          .hasSize(1);
    }
  }

  @Nested
  @DisplayName("PerimeterKind")
  class Perimeter {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
      "Personal, PERSONAL",
      "personal, PERSONAL",
      "PERSONALPERIMETER, PERSONAL",
      "Personal Perimeter 1, PERSONAL",
      "Enterprise, ENTERPRISE",
      "enterprise, ENTERPRISE",
      "EnterprisePerimeter, ENTERPRISE",
      "Work, UNKNOWN",
      "WorkPerimeter, UNKNOWN",
      "Default, PERSONAL",
      "DEFAULT, PERSONAL",
      "DefaultPerimeter, PERSONAL",
      "Other, UNKNOWN",
      "Corporate, UNKNOWN",
      "my personal, UNKNOWN"
    })
    @DisplayName("of reads the start of the word, in any case")
    void ofReadsTheStartOfTheWord(String text, PerimeterKind expected) {
      assertThat(PerimeterKind.of(text)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is unknown")
    @ValueSource(strings = {"", " "})
    @DisplayName("of reads an empty or a blank text as unknown")
    void ofReadsNothingAsUnknown(String text) {
      assertThat(PerimeterKind.of(text)).isEqualTo(PerimeterKind.UNKNOWN);
    }

    @Test
    @DisplayName("of refuses no text at all, saying what it needs")
    void ofNeedsAText() {
      assertThatNullPointerException().isThrownBy(() -> PerimeterKind.of(null)).withMessage("text");
    }

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"PERSONAL, personal", "ENTERPRISE, enterprise", "UNKNOWN, unknown"})
    @DisplayName("each kind has the name that the protocol of the daemon uses")
    void eachKindHasItsJsonName(PerimeterKind kind, String jsonName) {
      assertThat(kind.jsonName()).isEqualTo(jsonName);
    }
  }
}
