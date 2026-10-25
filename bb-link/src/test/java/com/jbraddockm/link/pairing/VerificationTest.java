package com.jbraddockm.link.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link Verification}. */
@DisplayName("Verification: whether the device took the new certificate")
class VerificationTest {

  private static final ServiceError REFUSED =
      new ServiceError(CoreErrorCode.COMMAND_FAILED, "peer not authenticated");

  @ParameterizedTest(name = "{0} is \"{1}\"")
  @CsvSource({"VERIFIED, verified", "FAILED, failed", "SKIPPED, skipped"})
  @DisplayName("each state has the name that the protocol of the daemon uses")
  void eachStateHasItsWireName(Verification.State state, String wireName) {
    assertThat(state.wireName()).isEqualTo(wireName);
  }

  @Test
  @DisplayName("verified has no error")
  void verifiedHasNoError() {
    assertThat(Verification.verified())
        .isEqualTo(new Verification(Verification.State.VERIFIED, null));
  }

  @Test
  @DisplayName("skipped has no error")
  void skippedHasNoError() {
    assertThat(Verification.skipped().state()).isEqualTo(Verification.State.SKIPPED);
    assertThat(Verification.skipped().error()).isNull();
  }

  @Test
  @DisplayName("failed holds the error that the device gave")
  void failedHoldsTheError() {
    assertThat(Verification.failed(REFUSED).state()).isEqualTo(Verification.State.FAILED);
    assertThat(Verification.failed(REFUSED).error()).isEqualTo(REFUSED);
  }

  @Test
  @DisplayName("toJson gives the state, and no error code or message when it did not fail")
  void toJsonWithoutAnError() {
    assertThat(Verification.verified().toJson())
        .containsEntry("state", "verified")
        .containsEntry("errorCode", null)
        .containsEntry("errorMessage", null);
  }

  @Test
  @DisplayName("toJson gives the code and the message of the error when it failed")
  void toJsonWithAnError() {
    assertThat(Verification.failed(REFUSED).toJson())
        .containsEntry("state", "failed")
        .containsEntry("errorCode", "COMMAND_FAILED")
        .containsEntry("errorMessage", "peer not authenticated");
  }
}
