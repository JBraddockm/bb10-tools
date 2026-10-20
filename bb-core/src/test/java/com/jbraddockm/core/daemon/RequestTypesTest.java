package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link RequestId}, {@link RequestRejectedException}, {@link ServiceInfo} and {@link
 * Establishment}.
 */
@DisplayName("Daemon types: RequestId, RequestRejectedException, ServiceInfo and Establishment")
class RequestTypesTest {

  @Nested
  @DisplayName("RequestId")
  class Ids {

    @Test
    @DisplayName(
        "is a whole number or a text, and nothing else: a fraction, a flag, null, a list or an"
            + " object is no identifier")
    void wholeNumberOrText() {
      assertThat(RequestId.from(7L)).contains(new RequestId.Numeric(7));
      assertThat(RequestId.from("abc")).contains(new RequestId.Text("abc"));
      assertThat(RequestId.from(1.5)).isEmpty();
      assertThat(RequestId.from(true)).isEmpty();
      assertThat(RequestId.from(null)).isEmpty();
      assertThat(RequestId.from(List.of(1))).isEmpty();
      assertThat(RequestId.from(Map.of())).isEmpty();
    }

    @Test
    @DisplayName("is written as it was read: a number as a number, a text as a text")
    void writtenAsItWasRead() {
      assertThat(new RequestId.Numeric(7).toJson()).isEqualTo(7L);
      assertThat(new RequestId.Text("abc").toJson()).isEqualTo("abc");
    }

    @Test
    @DisplayName("a number and the text of the same digits are two identifiers")
    void aNumberIsNotItsText() {
      assertThat(new RequestId.Numeric(7)).isNotEqualTo(new RequestId.Text("7"));
    }
  }

  @Test
  @DisplayName(
      "RequestRejectedException carries the error, says its words, and costs no stack trace: it is"
          + " how a request ends early")
  void aRejection() {
    var error = new ServiceError(CoreErrorCode.UNKNOWN_METHOD, "no such method");

    var rejection = new RequestRejectedException(error);

    assertThat(rejection.error()).isSameAs(error);
    assertThat(rejection).hasMessage("no such method").isInstanceOf(RuntimeException.class);
    assertThat(rejection.getStackTrace()).isEmpty();
  }

  @Test
  @DisplayName("ServiceInfo is the name of the tool and its version, both required")
  void serviceInfo() {
    assertThat(new ServiceInfo("bb-link", "0.1.0").tool()).isEqualTo("bb-link");
    assertThatNullPointerException()
        .isThrownBy(() -> new ServiceInfo(null, "1"))
        .withMessage("tool");
    assertThatNullPointerException()
        .isThrownBy(() -> new ServiceInfo("t", null))
        .withMessage("version");
  }

  @Test
  @DisplayName(
      "Establishment is either what was established, with its data, or the error that rejected it")
  void establishment() {
    var error = new ServiceError(CoreErrorCode.AUTH_FAILED, "no");

    assertThat(new Establishment.Established(Map.of("host", "h")).data())
        .containsEntry("host", "h");
    assertThat(new Establishment.Rejected(error).error()).isSameAs(error);
    assertThat(new Establishment.Rejected(error)).isInstanceOf(Establishment.class);
  }
}
