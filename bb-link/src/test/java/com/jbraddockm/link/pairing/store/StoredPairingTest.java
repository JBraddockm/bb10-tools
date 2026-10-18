package com.jbraddockm.link.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.link.testing.Resources;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * What {@link StoredPairing} keeps of a pairing besides the keys, and how it is written to and read
 * from {@code pairing.json}.
 *
 * <p>Resources, in {@code src/test/resources/com/jbraddockm/link/pairing/store}: {@code
 * stored-pairing--*.json}, the files that are read, and the one that is written.
 */
@DisplayName("StoredPairing: the pairing.json file of a pairing")
class StoredPairingTest {

  private static final StoredPairing PAIRING =
      new StoredPairing("01AB", "PC issuer 1", Instant.parse("2026-10-18T10:00:00Z"));

  private static String resource(String name) {
    return Resources.text(StoredPairingTest.class, name);
  }

  @Nested
  @DisplayName("writing")
  class Writing {

    @Test
    @DisplayName("toJsonText writes the three fields, compactly, in this order, and a newline")
    void writesTheThreeFields() {
      assertThat(PAIRING.toJsonText()).isEqualTo(resource("stored-pairing--complete.json"));
    }
  }

  @Nested
  @DisplayName("reading")
  class Reading {

    @Test
    @DisplayName("fromJsonText reads what toJsonText wrote")
    void readsWhatWasWritten() {
      assertThat(StoredPairing.fromJsonText(resource("stored-pairing--complete.json")))
          .isEqualTo(PAIRING);
    }

    @Test
    @DisplayName("fromJsonText still reads a file of update 39, which has a device name as well")
    void readsAFileThatHasAnExtraField() {
      assertThat(StoredPairing.fromJsonText(resource("stored-pairing--from-update-39.json")))
          .isEqualTo(PAIRING);
    }

    @ParameterizedTest(name = "{0}: {1}")
    @CsvSource({
      "stored-pairing--without-device-id.json, lacks deviceId",
      "stored-pairing--bad-date.json, is not readable",
      "stored-pairing--not-json.txt, is not readable"
    })
    @DisplayName("fromJsonText refuses a file that lacks a field, has a bad date, or is not JSON")
    void refusesWhatIsNotAPairingFile(String resource, String reason) {
      var text = resource(resource);

      assertThatIllegalArgumentException()
          .isThrownBy(() -> StoredPairing.fromJsonText(text))
          .withMessageContaining(reason);
    }
  }
}
