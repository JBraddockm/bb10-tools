package com.jbraddockm.link.transport.login;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link LoginStatus}. */
@DisplayName("LoginStatus: the status words of a login")
class LoginStatusTest {

  @ParameterizedTest(name = "\"{0}\" is {1}")
  @CsvSource({
    "PasswdChallenge, PASSWORD_CHALLENGE",
    "Success, SUCCESS",
    "Denied, DENIED",
    "Error, ERROR",
    "passwdchallenge, PASSWORD_CHALLENGE",
    "SUCCESS, SUCCESS",
    "dEnIeD, DENIED"
  })
  @DisplayName("of reads the words of the device in any case")
  void ofReadsTheWords(String word, LoginStatus expected) {
    assertThat(LoginStatus.of(word)).contains(expected);
  }

  @ParameterizedTest(name = "\"{0}\" is nothing")
  @NullAndEmptySource
  @ValueSource(strings = {"Ready", "PasswordChallenge", " Success", "Successful"})
  @DisplayName(
      "of gives nothing for a word that is not known, an empty text, or nothing, and does not strip"
          + " blanks")
  void ofGivesNothingForTheRest(String word) {
    assertThat(LoginStatus.of(word)).isEmpty();
  }
}
