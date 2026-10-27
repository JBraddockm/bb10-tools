package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link StaleSessionException}. */
@DisplayName("StaleSessionException: the device does not accept the session")
class StaleSessionExceptionTest {

  @Test
  @DisplayName("says whether it was the first exchange on the session")
  void saysWhetherItWasTheFirstExchange() {
    assertThat(new StaleSessionException(true).firstExchange()).isTrue();
    assertThat(new StaleSessionException(false).firstExchange()).isFalse();
  }

  @Test
  @DisplayName(
      "has a message, and no stack trace and no cause: it is a state to act on, not a fault to"
          + " debug")
  void isNotAFaultToDebug() {
    var exception = new StaleSessionException(false);

    assertThat(exception).hasMessage("the device does not accept the session").hasNoCause();
    assertThat(exception.getStackTrace()).isEmpty();
  }
}
