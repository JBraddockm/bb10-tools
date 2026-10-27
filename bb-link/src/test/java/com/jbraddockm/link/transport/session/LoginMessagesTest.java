package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link LoginMessages}: the wording of login failures. */
@DisplayName("LoginMessages: the words of the original tool for a login that failed")
class LoginMessagesTest {

  @Test
  @DisplayName("each message exists, and they are four different texts")
  void eachMessageExists() {
    var messages =
        List.of(
            LoginMessages.passwordRejected(2, 8),
            LoginMessages.invalidPassword(),
            LoginMessages.clockOutOfSync(),
            LoginMessages.systemError());

    assertThat(messages)
        .allSatisfy(message -> assertThat(message).isNotBlank())
        .doesNotHaveDuplicates();
  }

  @Test
  @DisplayName(
      "passwordRejected says how many attempts failed, and out of how many: the failed ones and the"
          + " remaining ones")
  void passwordRejectedGivesTheCounts() {
    assertThat(LoginMessages.passwordRejected(2, 8)).contains("2").contains("10");
  }

  @Test
  @DisplayName(
      "passwordRejected with no attempt remaining says that there are no more, and how many failed")
  void passwordRejectedWithNoneRemaining() {
    assertThat(LoginMessages.passwordRejected(10, 0))
        .contains("10")
        .isNotEqualTo(LoginMessages.passwordRejected(10, 1));
  }
}
