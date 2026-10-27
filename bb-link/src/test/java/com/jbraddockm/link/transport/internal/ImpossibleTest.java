package com.jbraddockm.link.transport.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.GeneralSecurityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link Impossible}: a checked exception that cannot happen is reported as a defect. */
@DisplayName("Impossible: code whose checked exception cannot happen")
class ImpossibleTest {

  private static final String REASON = "every JDK has this";

  @Test
  @DisplayName("gives what the task gives")
  void givesTheResult() {
    assertThat(Impossible.<String, GeneralSecurityException>call(() -> "result", REASON))
        .isEqualTo("result");
  }

  @Test
  @DisplayName(
      "reports a checked exception as an IllegalStateException with the reason and the cause")
  void reportsACheckedException() {
    var cause = new GeneralSecurityException("not there");

    assertThatThrownBy(
            () ->
                Impossible.call(
                    () -> {
                      throw cause;
                    },
                    REASON))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage(REASON)
        .hasCause(cause);
  }

  @Test
  @DisplayName("lets an unchecked exception pass as it is: it is not the one that cannot happen")
  void letsAnUncheckedExceptionPass() {
    var failure = new NumberFormatException("not a number");

    assertThatThrownBy(
            () ->
                Impossible.<String, GeneralSecurityException>call(
                    () -> {
                      throw failure;
                    },
                    REASON))
        .isSameAs(failure);
  }

  @Test
  @DisplayName("lets an error pass as it is")
  void letsAnErrorPass() {
    var error = new AssertionError("a defect elsewhere");

    assertThatThrownBy(
            () ->
                Impossible.<String, GeneralSecurityException>call(
                    () -> {
                      throw error;
                    },
                    REASON))
        .isSameAs(error);
  }
}
