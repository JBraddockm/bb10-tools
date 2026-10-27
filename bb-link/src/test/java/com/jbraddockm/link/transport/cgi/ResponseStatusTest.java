package com.jbraddockm.link.transport.cgi;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link ResponseStatus}. */
@DisplayName("ResponseStatus: the status words of a device")
class ResponseStatusTest {

  @ParameterizedTest(name = "\"{0}\" is {1}")
  @CsvSource({
    "Success, SUCCESS",
    "0, SUCCESS",
    "Ready, READY",
    "InProgress, IN_PROGRESS",
    "in progress, IN_PROGRESS",
    "Denied, DENIED",
    "PasswdChallenge, PASSWORD_CHALLENGE",
    "PasswordChallenge, PASSWORD_CHALLENGE",
    "Error, ERROR"
  })
  @DisplayName("of reads the words that the device is known to use")
  void ofReadsTheKnownWords(String word, ResponseStatus expected) {
    assertThat(ResponseStatus.of(word)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" is {1}")
  @CsvSource({
    "SUCCESS, SUCCESS",
    "iNpRoGrEsS, IN_PROGRESS",
    "'  error  ', ERROR",
    "'\tready\n', READY"
  })
  @DisplayName("of takes any case, and ignores the blanks around the word")
  void ofIgnoresCaseAndBlanks(String word, ResponseStatus expected) {
    assertThat(ResponseStatus.of(word)).isEqualTo(expected);
  }

  @ParameterizedTest(name = "\"{0}\" is UNKNOWN")
  @NullAndEmptySource
  @ValueSource(strings = {"garbage", "1", "successful", "in-progress"})
  @DisplayName("of gives UNKNOWN for a word that is not known, for nothing, and for an empty text")
  void ofGivesUnknownForTheRest(String word) {
    assertThat(ResponseStatus.of(word)).isEqualTo(ResponseStatus.UNKNOWN);
  }
}
