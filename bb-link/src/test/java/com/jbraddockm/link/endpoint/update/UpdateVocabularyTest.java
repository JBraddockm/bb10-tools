package com.jbraddockm.link.endpoint.update;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for the small enumerations of the update protocol: modes, phases, file kinds and statuses.
 */
@DisplayName("UpdateMode, UpdatePhase, UploadKind and UpdateStatus: the words of an update")
class UpdateVocabularyTest {

  @Nested
  @DisplayName("UpdateMode")
  class Mode {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "INSTALL_APPLICATIONS, bar",
      "INSTALL_OPERATING_SYSTEM, os",
      "UNINSTALL_APPLICATIONS, app"
    })
    @DisplayName("each mode is the word that the device is started with")
    void eachModeIsAWord(UpdateMode mode, String word) {
      assertThat(mode.wireName()).isEqualTo(word);
    }
  }

  @Nested
  @DisplayName("UpdatePhase")
  class Phase {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "CHECKING, checking",
      "UPLOADING, uploading",
      "INSTALLING, installing",
      "REMOVING, removing"
    })
    @DisplayName("each phase is the word that the daemon reports it as")
    void eachPhaseIsAWord(UpdatePhase phase, String word) {
      assertThat(phase.wireName()).isEqualTo(word);
    }
  }

  @Nested
  @DisplayName("UploadKind")
  class Kind {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"BAR, bar", "RADIO, radio"})
    @DisplayName("each kind is the word that the device is told the file is")
    void eachKindIsAWord(UploadKind kind, String word) {
      assertThat(kind.wireName()).isEqualTo(word);
    }
  }

  @Nested
  @DisplayName("UpdateStatus")
  class Status {

    @ParameterizedTest(name = "\"{0}\" is {1}")
    @CsvSource({
      "Ready, READY",
      "InProgress, IN_PROGRESS",
      "Success, SUCCESS",
      "Complete, COMPLETE",
      "Error, ERROR",
      "success, SUCCESS",
      "INPROGRESS, IN_PROGRESS",
      "'  Complete  ', COMPLETE"
    })
    @DisplayName("of reads the status of the device in any case, without the blanks around it")
    void ofReadsTheStatus(String text, UpdateStatus expected) {
      assertThat(UpdateStatus.of(text)).contains(expected);
    }

    @ParameterizedTest(name = "\"{0}\" is nothing")
    @ValueSource(strings = {"", " ", "Strange", "In Progress", "Done"})
    @DisplayName("of reads nothing for a word that is not a status, or for no word")
    void ofReadsNothingForOtherWords(String text) {
      assertThat(UpdateStatus.of(text)).isEmpty();
    }

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({
      "READY, Ready",
      "IN_PROGRESS, InProgress",
      "SUCCESS, Success",
      "COMPLETE, Complete",
      "ERROR, Error"
    })
    @DisplayName("each status is written as the device writes it")
    void eachStatusIsWrittenAsTheDeviceDoes(UpdateStatus status, String word) {
      assertThat(status.wireName()).isEqualTo(word);
    }
  }
}
