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

/** Tests for {@link CertificateRetrievalRequest}. */
@DisplayName("CertificateRetrievalRequest: what a request for a certificate is asked to do")
class CertificateRetrievalRequestTest {

  private static final Path DEVICES = Path.of("devices");

  private static CertificateRetrievalRequest requestWith(String host, int port) {
    return new CertificateRetrievalRequest("2A3B4C5D", DEVICES, host, false, true, port);
  }

  @Test
  @DisplayName("holds what it was given")
  void holdsWhatItWasGiven() {
    var request =
        new CertificateRetrievalRequest("2A3B4C5D", DEVICES, "workstation", true, false, 8443);

    assertThat(request.confirmPin()).isEqualTo("2A3B4C5D");
    assertThat(request.devicesDirectory()).isEqualTo(DEVICES);
    assertThat(request.hostName()).isEqualTo("workstation");
    assertThat(request.replace()).isTrue();
    assertThat(request.verify()).isFalse();
    assertThat(request.verifyPort()).isEqualTo(8443);
  }

  @Test
  @DisplayName("strips the blanks around a host name")
  void stripsTheHostName() {
    assertThat(requestWith("  workstation  ", 8443).hostName()).isEqualTo("workstation");
  }

  @Test
  @DisplayName("takes no host name, and then there is none")
  void takesNoHostName() {
    assertThat(requestWith(null, 8443).hostName()).isNull();
  }

  @ParameterizedTest(name = "\"{0}\" is not a host name")
  @ValueSource(strings = {"", "   ", "\t"})
  @DisplayName("refuses a host name that is blank, naming hostName")
  void refusesABlankHostName(String host) {
    assertThatThrownBy(() -> requestWith(host, 8443))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("hostName"));
  }

  @ParameterizedTest(name = "port {0} is a port")
  @ValueSource(ints = {1, 443, 8443, 65_535})
  @DisplayName("takes a port from 1 to 65535")
  void takesAPort(int port) {
    assertThat(requestWith(null, port).verifyPort()).isEqualTo(port);
  }

  @ParameterizedTest(name = "port {0} is not a port")
  @ValueSource(ints = {0, -1, 65_536, 100_000})
  @DisplayName("refuses a port outside 1 to 65535, naming verifyPort")
  void refusesAnotherPort(int port) {
    assertThatThrownBy(() -> requestWith(null, port))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("verifyPort"));
  }

  @ParameterizedTest(name = "\"{0}\" is not a PIN to confirm")
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  @DisplayName("refuses a PIN to confirm that is missing or blank, naming confirmPin")
  void refusesABlankPin(String pin) {
    assertThatThrownBy(() -> new CertificateRetrievalRequest(pin, DEVICES, null, false, true, 8443))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("confirmPin"));
  }

  @Test
  @DisplayName("refuses a missing folder of the devices")
  void refusesAMissingFolder() {
    assertThatNullPointerException()
        .isThrownBy(
            () -> new CertificateRetrievalRequest("2A3B4C5D", null, null, false, true, 8443))
        .withMessage("devicesDirectory");
  }
}
