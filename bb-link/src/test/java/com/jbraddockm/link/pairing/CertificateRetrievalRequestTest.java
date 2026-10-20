package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link CertificateRetrievalRequest}. */
@DisplayName("CertificateRetrievalRequest: what a request for a certificate is asked to do")
class CertificateRetrievalRequestTest {

  private static final Path DEVICES = Path.of("devices");

  private static CertificateRetrievalRequest requestWith(Optional<String> host, int port) {
    return new CertificateRetrievalRequest("2A3B4C5D", DEVICES, host, false, true, port);
  }

  @Test
  @DisplayName("holds what it was given")
  void holdsWhatItWasGiven() {
    var request =
        new CertificateRetrievalRequest(
            "2A3B4C5D", DEVICES, Optional.of("workstation"), true, false, 8443);

    assertThat(request.confirmPin()).isEqualTo("2A3B4C5D");
    assertThat(request.devicesDirectory()).isEqualTo(DEVICES);
    assertThat(request.hostName()).contains("workstation");
    assertThat(request.replace()).isTrue();
    assertThat(request.verify()).isFalse();
    assertThat(request.verifyPort()).isEqualTo(8443);
  }

  @Test
  @DisplayName("strips the blanks around a host name")
  void stripsTheHostName() {
    assertThat(requestWith(Optional.of("  workstation  "), 8443).hostName())
        .contains("workstation");
  }

  @Test
  @DisplayName("takes no host name, and then there is none")
  void takesNoHostName() {
    assertThat(requestWith(Optional.empty(), 8443).hostName()).isEmpty();
  }

  @ParameterizedTest(name = "\"{0}\" is not a host name")
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("refuses a host name that is blank, naming hostName")
  void refusesABlankHostName(String host) {
    assertThatThrownBy(() -> requestWith(Optional.of(host), 8443))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("hostName"));
  }

  @ParameterizedTest(name = "port {0} is a port")
  @ValueSource(ints = {1, 443, 8443, 65_535})
  @DisplayName("takes a port from 1 to 65535")
  void takesAPort(int port) {
    assertThat(requestWith(Optional.empty(), port).verifyPort()).isEqualTo(port);
  }

  @ParameterizedTest(name = "port {0} is not a port")
  @ValueSource(ints = {0, -1, 65_536, 100_000})
  @DisplayName("refuses a port outside 1 to 65535, naming verifyPort")
  void refusesAnotherPort(int port) {
    assertThatThrownBy(() -> requestWith(Optional.empty(), port))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("verifyPort"));
  }

  @ParameterizedTest(name = "\"{0}\" is not a PIN to confirm")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("refuses a PIN to confirm that is missing or blank, naming confirmPin")
  void refusesABlankPin(String pin) {
    assertThatThrownBy(
            () ->
                new CertificateRetrievalRequest(pin, DEVICES, Optional.empty(), false, true, 8443))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("confirmPin"));
  }

  @Test
  @DisplayName("refuses a missing folder of the devices")
  void refusesAMissingFolder() {
    assertThatNullPointerException()
        .isThrownBy(
            () ->
                new CertificateRetrievalRequest(
                    "2A3B4C5D", null, Optional.empty(), false, true, 8443))
        .withMessage("devicesDirectory");
  }
}
