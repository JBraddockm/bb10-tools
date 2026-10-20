package com.jbraddockm.link.actions;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PinReader}: extracting the PIN from the answer of the device. */
@DisplayName("PinReader: the PIN in the answer of the device")
class PinReaderTest {

  private static ResponseEnvelope answer(String resource) {
    return ResponseEnvelope.parse(Resources.text(PinReaderTest.class, resource));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"pin--success.xml", "pin--without-prefix.xml", "pin--with-blanks.xml"})
  @DisplayName(
      "reads the PIN after the last X, or the whole text when there is no X, and strips the blanks")
  void readsThePin(String resource) {
    assertThat(finished(PinReader.read(answer(resource)))).isEqualTo(new DevicePin("2A3B4C5D"));
  }

  @ParameterizedTest(name = "{0}")
  @ValueSource(strings = {"pin--only-the-prefix.xml", "pin--empty.xml", "pin--missing.xml"})
  @DisplayName(
      "fails when there is no PIN in the answer: only the prefix, nothing, or no element at all")
  void failsWithoutAPin(String resource) {
    var error = failed(PinReader.read(answer(resource)));

    assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
    assertThat(error.message()).isEqualTo("The device answered without a PIN");
    assertThat(error.details()).containsEntry("element", "DevicePIN");
  }

  @Test
  @DisplayName("fails with the error that the device reported, with its words and its number")
  void failsWithTheErrorOfTheDevice() {
    var error = failed(PinReader.read(answer("pin--error.xml")));

    assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
    assertThat(error.message()).isEqualTo("Error - something failed");
    assertThat(error.details()).containsEntry("errorIdentifier", 1011);
  }

  @Test
  @DisplayName("fails when the answer is about something else than the PIN")
  void failsForAnotherElement() {
    var envelope = answer("pin--wrong-element.xml");

    assertThat(failed(PinReader.read(envelope)))
        .isEqualTo(ResponseFailures.notExpected(envelope, ResponseElement.DEVICE_PIN));
  }
}
