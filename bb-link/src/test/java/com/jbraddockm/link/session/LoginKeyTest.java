package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests for {@link LoginKey}. */
@DisplayName("LoginKey: the elements of a login answer, by the root that wrote them")
class LoginKeyTest {

  @ParameterizedTest(name = "{0}: {1} or {2}")
  @CsvSource({
    "STATUS, Status, Status",
    "RETRIES_REMAINING, RetriesRemaining, retriesremaining",
    "FAILED_ATTEMPTS, FailedAttempts, failedattempts",
    "CHALLENGE, Challenge, challenge",
    "ALGORITHM, Algorithm, algorithm",
    "ITERATION_COUNT, ICount, icount",
    "SALT, Salt, salt"
  })
  @DisplayName("elementName gives the current name, or the legacy name for a legacy root")
  void elementNameDependsOnTheRoot(LoginKey key, String current, String legacy) {
    assertThat(key.elementName(false)).isEqualTo(current);
    assertThat(key.elementName(true)).isEqualTo(legacy);
  }
}
