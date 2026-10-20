package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link SmbCredentials}. */
@DisplayName("SmbCredentials: the user and the password of the share of a device")
class SmbCredentialsTest {

  private static final SmbCredentials CREDENTIALS =
      new SmbCredentials("dtm", "abc123", "WORKGROUP", "BLACKBERRY");

  @Test
  @DisplayName(
      "toJson gives the user, the password, the workgroup and the NetBIOS name: the caller asked"
          + " for them")
  void toJsonGivesEverything() {
    assertThat(CREDENTIALS.toJson())
        .containsEntry("user", "dtm")
        .containsEntry("password", "abc123")
        .containsEntry("workgroup", "WORKGROUP")
        .containsEntry("netBios", "BLACKBERRY");
    assertThat(CREDENTIALS.toJson().keySet())
        .containsExactly("user", "password", "workgroup", "netBios");
  }

  @Test
  @DisplayName("toJson gives null for a workgroup and a NetBIOS name that the device did not give")
  void toJsonGivesNullForWhatIsMissing() {
    assertThat(new SmbCredentials("dtm", "abc123", null, null).toJson())
        .containsEntry("workgroup", null)
        .containsEntry("netBios", null);
  }

  @Test
  @DisplayName("toString says the user and hides the password, so that a log never holds it")
  void toStringHidesThePassword() {
    assertThat(CREDENTIALS.toString())
        .isEqualTo("SmbCredentials[user=dtm, password=***]")
        .doesNotContain("abc123");
  }

  @Test
  @DisplayName("a user and a password are required")
  void aUserAndAPasswordAreRequired() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SmbCredentials(null, "x", null, null))
        .withMessage("user");
    assertThatNullPointerException()
        .isThrownBy(() -> new SmbCredentials("x", null, null, null))
        .withMessage("password");
  }
}
