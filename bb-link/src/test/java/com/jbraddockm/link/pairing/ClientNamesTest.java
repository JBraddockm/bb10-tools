package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ClientNames}: the common name of the requested certificate. */
@DisplayName("ClientNames: the parts of the common name of a certificate")
class ClientNamesTest {

  @Nested
  @DisplayName("sanitise")
  class Sanitise {

    @ParameterizedTest(name = "\"{0}\" is \"{1}\"")
    @CsvSource({
      "workstation, workstation",
      "my host, my-host",
      "mac_book.local, mac_book.local",
      "'  padded  ', padded",
      "a&b=c, a-b-c",
      "é, -",
      "a/b\\c, a-b-c"
    })
    @DisplayName(
        "keeps letters, digits, dot, underscore and dash, and writes every other character as a"
            + " dash")
    void keepsTheSafeCharacters(String name, String expected) {
      assertThat(ClientNames.sanitise(name)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is \"unknown\"")
    @ValueSource(strings = {"", "   ", "\t\n"})
    @DisplayName("gives \"unknown\" for a name that is blank")
    void givesUnknownForABlankName(String name) {
      assertThat(ClientNames.sanitise(name)).isEqualTo("unknown");
    }
  }

  @Nested
  @DisplayName("pinHex")
  class PinHex {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({
      "2A3B4C5D, 2A3B4C5D",
      "2a3b4c5d, 2A3B4C5D",
      "0x2a3b4c5d, 2A3B4C5D",
      "0X2A3B4C5D, 2A3B4C5D",
      "'  2a3b4c5d  ', 2A3B4C5D",
      "00000001, 1",
      "0000000A, A",
      "00000000, 0",
      "FFFFFFFF, FFFFFFFF",
      "FFFFFFFFFFFFFFFF, FFFFFFFFFFFFFFFF"
    })
    @DisplayName(
        "writes the PIN in capitals without leading zeros, as the Windows client writes it in its"
            + " certificates")
    void writesTheHexWithoutLeadingZeros(String pin, String expected) {
      assertThat(ClientNames.pinHex(pin)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is not a PIN")
    @ValueSource(strings = {"", "xyz", "2A3B4C5G", "FFFFFFFFFFFFFFFFF", "-1"})
    @DisplayName("fails for what is not hexadecimal, or does not fit in 64 bits")
    void failsForWhatIsNotHex(String pin) {
      assertThatExceptionOfType(NumberFormatException.class)
          .isThrownBy(() -> ClientNames.pinHex(pin));
    }
  }

  @Test
  @DisplayName(
      "localWorkstation is the name of this computer, safe to write, without the domain, and never"
          + " blank")
  void localWorkstationIsAnAcceptableName() {
    var name = ClientNames.localWorkstation();

    assertThat(name).isNotBlank().matches("[A-Za-z0-9._-]+");
    assertThat(name.indexOf('.')).as("the part after the first dot is cut").isLessThanOrEqualTo(0);
  }
}
