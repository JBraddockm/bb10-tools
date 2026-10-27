package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.link.testing.Resources;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link StoredIdentity}: its JSON form in {@code identity.json}. */
@DisplayName("StoredIdentity: the identity.json file of a certificate")
class StoredIdentityTest {

  private static final StoredIdentity IDENTITY =
      new StoredIdentity("BBCGIAUTH [2A3B4C5D] workstation", Instant.parse("2026-10-18T10:00:00Z"));

  private static String resource(String name) {
    return Resources.text(StoredIdentityTest.class, name);
  }

  @Test
  @DisplayName("toJsonText writes the two fields, compactly, in this order, and a newline")
  void writesTheTwoFields() {
    assertThat(IDENTITY.toJsonText()).isEqualTo(resource("stored-identity--complete.json"));
  }

  @Nested
  @DisplayName("fromJsonText")
  class Reading {

    @Test
    @DisplayName("reads what toJsonText wrote")
    void readsWhatWasWritten() {
      assertThat(StoredIdentity.fromJsonText(resource("stored-identity--complete.json")))
          .isEqualTo(IDENTITY);
    }

    @Test
    @DisplayName("still reads a file of update 36, which has a device identifier as well")
    void readsAFileThatHasAnExtraField() {
      assertThat(StoredIdentity.fromJsonText(resource("stored-identity--from-update-36.json")))
          .isEqualTo(IDENTITY);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
      "stored-identity--without-common-name.json, lacks commonName",
      "stored-identity--bad-date.json, is not readable",
      "stored-identity--not-json.txt, is not readable",
      "stored-identity--array.json, lacks commonName",
      "stored-identity--common-name-not-text.json, lacks commonName"
    })
    @DisplayName("refuses a file that lacks a field, has a bad date, or is not JSON")
    void refusesWhatIsNotAnIdentityFile(String name, String reason) {
      var text = resource(name);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> StoredIdentity.fromJsonText(text))
          .withMessageContaining(reason);
    }
  }

  @Test
  @DisplayName("an identity has a name and a date")
  void anIdentityHasANameAndADate() {
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredIdentity(null, Instant.EPOCH))
        .withMessage("commonName");
    assertThatNullPointerException()
        .isThrownBy(() -> new StoredIdentity("x", null))
        .withMessage("createdAt");
  }
}
