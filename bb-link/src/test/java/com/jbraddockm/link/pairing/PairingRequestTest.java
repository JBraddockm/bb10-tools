package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PairingRequest}. */
@DisplayName("PairingRequest: what a pairing is asked to do")
class PairingRequestTest {

  private static final Path DEVICES = Path.of("devices");

  @Test
  @DisplayName("holds the PIN to confirm, the folder of the devices, and whether to replace")
  void holdsWhatItWasGiven() {
    var request = new PairingRequest("2A3B4C5D", DEVICES, true);

    assertThat(request.confirmPin()).isEqualTo("2A3B4C5D");
    assertThat(request.devicesDirectory()).isEqualTo(DEVICES);
    assertThat(request.replace()).isTrue();
  }

  @ParameterizedTest(name = "\"{0}\" is not a PIN to confirm")
  @NullAndEmptySource
  @ValueSource(strings = {"   ", "\t"})
  @DisplayName("refuses a PIN to confirm that is missing or blank, naming confirmPin")
  void refusesABlankPin(String pin) {
    assertThatThrownBy(() -> new PairingRequest(pin, DEVICES, false))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("confirmPin"));
  }

  @Test
  @DisplayName("refuses a missing folder of the devices")
  void refusesAMissingFolder() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PairingRequest("2A3B4C5D", null, false))
        .withMessage("devicesDirectory");
  }
}
