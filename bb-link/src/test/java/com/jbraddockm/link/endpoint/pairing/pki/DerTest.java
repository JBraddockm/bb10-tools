package com.jbraddockm.link.endpoint.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigInteger;
import java.time.Instant;
import java.util.HexFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Der}: the bytes written for each kind of element. */
@DisplayName("Der: the elements of a certificate, written as DER")
class DerTest {

  private static final HexFormat HEX = HexFormat.of().withUpperCase();

  private static String hex(byte[] bytes) {
    return HEX.formatHex(bytes);
  }

  @Nested
  @DisplayName("integers")
  class Integers {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--integer.csv",
        numLinesToSkip = 1)
    @DisplayName("integer(long) writes the shortest two's complement")
    void integerOfALong(long value, String expected) {
      assertThat(hex(Der.integer(value))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--integer.csv",
        numLinesToSkip = 1)
    @DisplayName("integer(BigInteger) writes the same as integer(long) for the same number")
    void integerOfABigIntegerAgrees(BigInteger value, String expected) {
      assertThat(hex(Der.integer(value))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--big-integer.csv",
        numLinesToSkip = 1)
    @DisplayName("integer(BigInteger) writes numbers that a long cannot hold")
    void integerBeyondALong(BigInteger value, String expected) {
      assertThat(hex(Der.integer(value))).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("lengths")
  class Lengths {

    @ParameterizedTest(name = "{0} bytes: header {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--length.csv",
        numLinesToSkip = 1)
    @DisplayName(
        "a length under 128 is one byte, a longer one is 0x80 plus the count of its bytes, then the"
            + " bytes")
    void lengthIsWrittenInTheShortestForm(int contentLength, String header) {
      var content = new byte[contentLength];

      var encoded = Der.sequence(content);

      assertThat(hex(encoded)).startsWith(header);
      assertThat(encoded).hasSize(header.length() / 2 + contentLength);
    }

    @Test
    @DisplayName("the content of an element follows its header unchanged")
    void contentFollowsTheHeader() {
      var content = new byte[] {1, 2, 3};

      assertThat(hex(Der.sequence(content))).isEqualTo("3003010203");
    }
  }

  @Nested
  @DisplayName("object identifiers")
  class ObjectIdentifiers {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvFileSource(
        resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--object-identifier.csv",
        numLinesToSkip = 1)
    @DisplayName(
        "objectIdentifier writes the first two arcs as one number, and each arc in base 128")
    void objectIdentifierIsWrittenInBase128(String dotted, String expected) {
      assertThat(hex(Der.objectIdentifier(dotted))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is refused")
    @ValueSource(strings = {"", "1", "3.1", "1.40", "-1.2", "1.-2", "a.b"})
    @DisplayName(
        "objectIdentifier refuses what is not an identifier: one arc, a first arc over 2, a second"
            + " over 39 under 0 or 1, a sign, a letter")
    void objectIdentifierRefusesWhatIsNotOne(String dotted) {
      assertThatIllegalArgumentException().isThrownBy(() -> Der.objectIdentifier(dotted));
    }
  }

  @Nested
  @DisplayName("times")
  class Times {

    @ParameterizedTest(name = "{0} is {1}")
    @CsvFileSource(resources = "/com/jbraddockm/link/endpoint/pairing/pki/der--time.csv", numLinesToSkip = 1)
    @DisplayName(
        "time is a UTCTime from 1950 to 2049, as the standard says, and a GeneralizedTime outside"
            + " them")
    void timeChoosesTheFormByTheYear(Instant instant, String expected) {
      assertThat(hex(Der.time(instant))).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("simple values")
  class Simple {

    @Test
    @DisplayName("nullValue writes 05 00")
    void nullValueIsFiveZero() {
      assertThat(hex(Der.nullValue())).isEqualTo("0500");
    }

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({"'', 0C00", "a, 0C0161", "é, 0C02C3A9", "€, 0C03E282AC"})
    @DisplayName("utf8String writes the bytes of UTF-8, so that é is two bytes and € is three")
    void utf8StringWritesUtf8(String text, String expected) {
      assertThat(hex(Der.utf8String(text))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"'', 030100", "01, 03020001", "0102, 0303000102", "FF00, 030300FF00"})
    @DisplayName("bitString puts a byte of 0 unused bits before the bytes")
    void bitStringStartsWithUnusedBits(String content, String expected) {
      assertThat(hex(Der.bitString(HEX.parseHex(content)))).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} is {1}")
    @CsvSource({"'', 0400", "01, 040101", "0102, 04020102"})
    @DisplayName("octetString writes the bytes as they are")
    void octetStringWritesTheBytes(String content, String expected) {
      assertThat(hex(Der.octetString(HEX.parseHex(content)))).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("constructed values")
  class Constructed {

    @Test
    @DisplayName("sequence writes its parts one after the other, and an empty one is 30 00")
    void sequenceJoinsItsParts() {
      assertThat(hex(Der.sequence())).isEqualTo("3000");
      assertThat(hex(Der.sequence(Der.integer(1), Der.nullValue()))).isEqualTo("30050201010500");
    }

    @Test
    @DisplayName("set is a sequence with the tag 31, and its parts are written in the order given")
    void setKeepsTheOrderGiven() {
      assertThat(hex(Der.set())).isEqualTo("3100");
      assertThat(hex(Der.set(Der.integer(2), Der.integer(1))))
          .isEqualTo("31060201020201 01".replace(" ", ""));
    }

    @Test
    @DisplayName("sequences inside sequences are lengths inside lengths")
    void sequencesNest() {
      assertThat(hex(Der.sequence(Der.sequence(Der.nullValue())))).isEqualTo("30043002" + "0500");
    }

    @ParameterizedTest(name = "[{0}] is {1}")
    @CsvSource({"0, A000", "1, A100", "3, A300", "30, BE00"})
    @DisplayName("contextual writes a constructed context tag with the number, 0 to 30")
    void contextualWritesTheTag(int number, String expected) {
      assertThat(hex(Der.contextual(number))).isEqualTo(expected);
    }

    @Test
    @DisplayName("contextual holds what it is given")
    void contextualHoldsItsParts() {
      assertThat(hex(Der.contextual(0, Der.integer(2)))).isEqualTo("A003020102");
    }

    @ParameterizedTest(name = "[{0}] is refused")
    @ValueSource(ints = {-1, 31, 100})
    @DisplayName("contextual refuses a number outside 0 to 30")
    void contextualRefusesOtherNumbers(int number) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> Der.contextual(number))
          .withMessageContaining("out of range");
    }

    @Test
    @DisplayName("concatenate joins the parts in order, and gives nothing for no parts")
    void concatenateJoins() {
      assertThat(Der.concatenate()).isEmpty();
      assertThat(hex(Der.concatenate(new byte[] {1}, new byte[] {}, new byte[] {2, 3})))
          .isEqualTo("010203");
    }
  }
}
