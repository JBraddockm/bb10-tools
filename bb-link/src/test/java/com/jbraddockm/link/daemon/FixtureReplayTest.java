package com.jbraddockm.link.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.testing.Resources;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link FixtureReplay}: cutting a recording into device answers at separator lines. */
@DisplayName("FixtureReplay.split: a recording cut into the answers of a device")
class FixtureReplayTest {

  private static List<String> split(String resource) {
    return FixtureReplay.split(Resources.text(FixtureReplayTest.class, resource));
  }

  @Test
  @DisplayName("a recording with no separator is one answer, as it is")
  void noSeparator() {
    assertThat(split("fixture-replay--no-separator.txt")).containsExactly("<a/>\n");
  }

  @Test
  @DisplayName("two answers are cut at the line of dashes, which is not part of either")
  void twoAnswers() {
    assertThat(split("fixture-replay--two-answers.txt")).containsExactly("one\n", "two\n");
  }

  @Test
  @DisplayName("three answers are cut at both separators")
  void threeAnswers() {
    assertThat(split("fixture-replay--three-answers.txt")).containsExactly("a\n", "b\n", "c");
  }

  @Test
  @DisplayName("the separator may be followed by blanks, or end in a carriage return")
  void aSeparatorWithBlanksOrCarriageReturn() {
    assertThat(split("fixture-replay--separator-with-blanks.txt"))
        .containsExactly("one\n", "two\n");
    assertThat(split("fixture-replay--separator-crlf.txt")).containsExactly("one\r\n", "two\r\n");
  }

  @Test
  @DisplayName(
      "a separator at the end leaves an empty answer after it, since the device may have answered"
          + " with nothing")
  void aSeparatorAtTheEnd() {
    assertThat(split("fixture-replay--separator-at-the-end.txt")).containsExactly("one\n", "");
  }

  @Test
  @DisplayName(
      "dashes are a separator only alone on their line: not inside a line, not four of them, not"
          + " with a word after")
  void dashesThatAreNotASeparator() {
    assertThat(split("fixture-replay--dashes-in-a-line.txt")).hasSize(1);
  }

  @Test
  @DisplayName(
      "a block of a certificate is not cut apart: its lines of five dashes are not separators")
  void aCertificateBlockIsNotCutApart() {
    assertThat(split("fixture-replay--pem-block.txt")).hasSize(1);
  }

  @Test
  @DisplayName("three dashes with more text on the line are none")
  void threeDashesAndText() {
    assertThat(split("fixture-replay--three-dashes-and-text.txt")).hasSize(1);
  }

  @Test
  @DisplayName("a separator at the very end, with no newline after it, leaves an empty answer")
  void aSeparatorWithoutANewline() {
    assertThat(split("fixture-replay--separator-without-a-newline.txt")).containsExactly("a\n", "");
  }

  @Test
  @DisplayName("an empty recording is one empty answer")
  void anEmptyRecording() {
    assertThat(split("fixture-replay--empty.txt")).containsExactly("");
  }
}
