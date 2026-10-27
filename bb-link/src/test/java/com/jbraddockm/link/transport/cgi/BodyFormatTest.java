package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link BodyFormat}. */
@DisplayName("BodyFormat: the format that a body of an answer is reported in")
class BodyFormatTest {

  @ParameterizedTest(name = "{0} is \"{1}\"")
  @CsvSource({"JSON, json", "XML, xml", "TEXT, text"})
  @DisplayName("each format has the name that the protocol of the daemon uses")
  void eachFormatHasItsWireName(BodyFormat format, String wireName) {
    assertThat(format.wireName()).isEqualTo(wireName);
  }
}
