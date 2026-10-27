package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link FormEncoding}: UTF-8 percent-encoding of form and query text as defined by RFC
 * 3986.
 */
@DisplayName("FormEncoding: text in a form body or a query")
class FormEncodingTest {

  @ParameterizedTest(name = "\"{0}\" is written \"{1}\"")
  @CsvSource({
    "Get Dynamic Properties, Get%20Dynamic%20Properties",
    "a&b=c, a%26b%3Dc",
    "'a/b?c#d', a%2Fb%3Fc%23d",
    "'100%', 100%25",
    "é, %C3%A9",
    "€, %E2%82%AC",
    "😀, %F0%9F%98%80",
    "-_.~AZaz09, -_.~AZaz09"
  })
  @DisplayName("encode writes the reserved characters and the bytes of UTF-8 as %XX in capitals")
  void encodeWritesReservedCharacters(String text, String expected) {
    assertThat(FormEncoding.encode(text)).isEqualTo(expected);
  }

  @Test
  @DisplayName("encode gives an empty string for an empty text")
  void encodeGivesNothingForNothing() {
    assertThat(FormEncoding.encode("")).isEmpty();
  }

  @Test
  @DisplayName("encode writes a line break as %0A, which is what a certificate in PEM needs")
  void encodeWritesLineBreaks() {
    assertThat(FormEncoding.encode("\n")).isEqualTo("%0A");
  }

  @Test
  @DisplayName("encode leaves exactly the letters, digits, dash, underscore, dot and tilde alone")
  void encodeLeavesTheUnreservedCharacters() {
    var unreserved =
        IntStream.rangeClosed(0, 127)
            .filter(code -> FormEncoding.encode(String.valueOf((char) code)).length() == 1)
            .mapToObj(code -> String.valueOf((char) code))
            .toList();

    assertThat(unreserved)
        .allMatch(character -> character.matches("[A-Za-z0-9._~-]"))
        .hasSize(26 + 26 + 10 + 4);
  }

  @Test
  @DisplayName("encode writes every other ASCII character as % and two capitals")
  void encodeWritesTheOthersAsPercentAndTwoCapitals() {
    var others =
        IntStream.rangeClosed(0, 127)
            .mapToObj(code -> String.valueOf((char) code))
            .filter(character -> !character.matches("[A-Za-z0-9._~-]"))
            .toList();

    assertThat(others)
        .isNotEmpty()
        .allSatisfy(
            character -> assertThat(FormEncoding.encode(character)).matches("%[0-9A-F]{2}"));
  }
}
