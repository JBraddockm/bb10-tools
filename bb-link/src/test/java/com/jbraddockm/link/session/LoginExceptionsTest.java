package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for the exceptions that report a failed login: messages, causes, and the absence of stack
 * traces.
 */
@DisplayName("LoginException: the ways that a login fails")
class LoginExceptionsTest {

  @Test
  @DisplayName("is sealed to five kinds, and no other")
  void isSealedToFiveKinds() {
    assertThat(Set.of(LoginException.class.getPermittedSubclasses()))
        .containsExactlyInAnyOrder(
            PasswordRejectedException.class,
            InvalidPasswordException.class,
            ClockOutOfSyncException.class,
            PasswordRequiredException.class,
            UnusableLoginResponseException.class);
  }

  @Test
  @DisplayName("has no stack trace and no cause, whichever kind it is")
  void isNotAFaultToDebug() {
    for (LoginException failure :
        new LoginException[] {
          new PasswordRejectedException(1, 9),
          new InvalidPasswordException(),
          new ClockOutOfSyncException(),
          new PasswordRequiredException(),
          new UnusableLoginResponseException("x")
        }) {
      assertThat(failure.getStackTrace()).isEmpty();
      assertThat(failure).hasNoCause();
      assertThat(failure.getMessage()).isNotBlank();
    }
  }

  @Nested
  @DisplayName("PasswordRejectedException")
  class Rejected {

    @Test
    @DisplayName(
        "holds the failed and the remaining attempts, and says so in the words of the original"
            + " tool")
    void holdsTheAttempts() {
      var rejected = new PasswordRejectedException(2, 8);

      assertThat(rejected.failedAttempts()).isEqualTo(2);
      assertThat(rejected.remainingAttempts()).isEqualTo(8);
      assertThat(rejected.isLocked()).isFalse();
      assertThat(rejected).hasMessage(LoginMessages.passwordRejected(2, 8));
    }

    @Test
    @DisplayName("is locked when no attempt remains, and the message says that there are no more")
    void isLockedWhenNoAttemptRemains() {
      var locked = new PasswordRejectedException(10, 0);

      assertThat(locked.isLocked()).isTrue();
      assertThat(locked).hasMessage(LoginMessages.passwordRejected(10, 0));
      assertThat(locked.getMessage())
          .isNotEqualTo(new PasswordRejectedException(10, 1).getMessage());
    }
  }

  @Test
  @DisplayName(
      "PasswordRequiredException says that a password was asked for and none was given, and that"
          + " nothing was tried")
  void passwordRequired() {
    assertThat(new PasswordRequiredException())
        .hasMessage("The device asks for a password and none was given; no login attempt was made");
  }

  @Test
  @DisplayName(
      "UnusableLoginResponseException has the message of a system error, and the detail apart")
  void unusableResponse() {
    var unusable = new UnusableLoginResponseException("Unknown status: Strange");

    assertThat(unusable.extra()).isEqualTo("Unknown status: Strange");
    assertThat(unusable).hasMessage(LoginMessages.systemError());
  }

  @Test
  @DisplayName(
      "InvalidPasswordException and ClockOutOfSyncException have the messages of the original tool")
  void theTwoOthers() {
    assertThat(new InvalidPasswordException()).hasMessage(LoginMessages.invalidPassword());
    assertThat(new ClockOutOfSyncException()).hasMessage(LoginMessages.clockOutOfSync());
  }
}
