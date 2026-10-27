package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.link.testing.FixedRandom;
import com.jbraddockm.link.testing.Resources;
import java.security.SecureRandom;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link DeviceId}: the identifier a computer gives itself when it pairs with a device.
 */
@DisplayName("DeviceId: the identifier that a computer gives itself when it pairs")
class DeviceIdTest {

  @Test
  @DisplayName("generate gives 01 and 32 hexadecimal digits in capitals")
  void generateGivesTheFormatOfLink() {
    assertThat(DeviceId.generate(new SecureRandom())).matches("01[0-9A-F]{32}");
  }

  @Test
  @DisplayName("generate gives a different identifier each time")
  void generateGivesANewOneEachTime() {
    var random = new SecureRandom();

    assertThat(DeviceId.generate(random)).isNotEqualTo(DeviceId.generate(random));
  }

  @Test
  @DisplayName("generate writes each random byte as two capitals after the 01")
  void generateWritesTheBytesAsCapitals() {
    var expected = Resources.text(DeviceIdTest.class, "device-id--from-fixed-bytes.txt").strip();

    assertThat(DeviceId.generate(new FixedRandom(0xAB))).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" is written \"{1}\"")
  @CsvSource({"ab12,AB12", "'  ab12  ',AB12", "a-b_c,A-B_C", "AB12,AB12", "0a,0A"})
  @DisplayName("normalise strips the blanks and writes the letters in capitals")
  void normaliseWritesInCapitals(String typed, String expected) {
    assertThat(DeviceId.normalise(typed)).isEqualTo(expected);
  }

  @Test
  @DisplayName("normalise takes an identifier of 64 characters, the longest there is")
  void normaliseTakesTheLongest() {
    var longest = Resources.text(DeviceIdTest.class, "device-id--64-characters.txt").strip();

    assertThat(DeviceId.normalise(longest)).isEqualTo(longest.toUpperCase(Locale.ROOT));
  }

  @ParameterizedTest(name = "{0} is refused")
  @ValueSource(
      strings = {
        "device-id--blank.txt",
        "device-id--with-space.txt",
        "device-id--65-characters.txt",
        "device-id--not-ascii.txt"
      })
  @DisplayName("normalise refuses what is not 1 to 64 letters, digits, dashes or underscores")
  void normaliseRefusesWhatIsNotAnIdentifier(String resource) {
    var typed = Resources.text(DeviceIdTest.class, resource);

    assertThatIllegalArgumentException()
        .isThrownBy(() -> DeviceId.normalise(typed))
        .withMessageContaining("is not an identifier");
  }
}
