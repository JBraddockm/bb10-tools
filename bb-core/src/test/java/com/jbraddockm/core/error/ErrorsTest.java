package com.jbraddockm.core.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.core.testing.Resources;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link CoreErrorCode}, {@link ServiceError} and {@link InvalidArgumentException}. */
@DisplayName("Errors: the codes, ServiceError and InvalidArgumentException")
class ErrorsTest {

  @Nested
  @DisplayName("CoreErrorCode")
  class Codes {

    @Test
    @DisplayName(
        "has these thirteen codes, in this order: the order that the protocol document lists them"
            + " in")
    void hasThirteenCodes() {
      var expected =
          Resources.text(ErrorsTest.class, "core-error-code--names.txt").lines().toList();

      assertThat(Arrays.stream(CoreErrorCode.values()).map(Enum::name))
          .containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(CoreErrorCode.class)
    @DisplayName("is written on the wire as its own name, and is an error code")
    void isWrittenAsItsName(CoreErrorCode code) {
      assertThat(code.wireName()).isEqualTo(code.name());
      assertThat(code).isInstanceOf(ErrorCode.class);
    }
  }

  @Nested
  @DisplayName("ServiceError")
  class Service {

    @Test
    @DisplayName(
        "is a code, the words and the details, and the details are none when none were given or"
            + " when they are null")
    void isCodeWordsAndDetails() {
      assertThat(new ServiceError(CoreErrorCode.IO_ERROR, "no space left").details()).isEmpty();
      assertThat(new ServiceError(CoreErrorCode.IO_ERROR, "no space left", null).details())
          .isEmpty();
      assertThat(
              new ServiceError(CoreErrorCode.IO_ERROR, "no space left", Map.of("path", "/tmp"))
                  .details())
          .containsEntry("path", "/tmp");
    }

    @Test
    @DisplayName("needs a code and the words")
    void needsCodeAndWords() {
      assertThatNullPointerException()
          .isThrownBy(() -> new ServiceError(null, "x"))
          .withMessage("code");
      assertThatNullPointerException()
          .isThrownBy(() -> new ServiceError(CoreErrorCode.INTERNAL, null))
          .withMessage("message");
    }

    @Test
    @DisplayName(
        "keeps its own copy of the details, in the order they were given, which nobody can change")
    void keepsItsOwnCopyOfTheDetails() {
      var given = new LinkedHashMap<String, Object>();
      given.put("b", 1);
      given.put("a", 2);
      var error = new ServiceError(CoreErrorCode.INTERNAL, "x", given);

      given.put("c", 3);

      assertThat(List.copyOf(error.details().keySet())).containsExactly("b", "a");
      assertThatThrownBy(() -> error.details().put("d", 4))
          .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName(
        "is written without its details when it has none, and with them in order when it has")
    void isWrittenAsJson() {
      var plain = new ServiceError(CoreErrorCode.IO_ERROR, "no space left");
      var detailed =
          new ServiceError(
              CoreErrorCode.INVALID_ARGUMENT,
              "'port' must be between 1 and 65535",
              Json.object("param", "port", "limit", 65535));

      assertThat(List.copyOf(plain.toJson().keySet()))
          .containsExactly("code", "message", "details");
      assertThat(Json.write(plain.toJson()))
          .isEqualTo(
              Resources.text(ErrorsTest.class, "service-error--without-details.json").strip());
      assertThat(Json.write(detailed.toJson()))
          .isEqualTo(Resources.text(ErrorsTest.class, "service-error--with-details.json").strip());
    }

    @Test
    @DisplayName(
        "internal is an INTERNAL error with the words of the cause, or the name of its class when"
            + " the cause has none, and the class of the cause as a detail")
    void anInternalError() {
      var withWords = ServiceError.internal(new IllegalStateException("boom"));
      var withoutWords = ServiceError.internal(new IllegalStateException());

      assertThat(withWords.code()).isEqualTo(CoreErrorCode.INTERNAL);
      assertThat(withWords.message()).isEqualTo("boom");
      assertThat(withWords.details()).containsEntry("exception", "java.lang.IllegalStateException");
      assertThat(withoutWords.message()).isEqualTo("IllegalStateException");
    }
  }

  @Nested
  @DisplayName("InvalidArgumentException")
  class InvalidArgument {

    @Test
    @DisplayName(
        "is an IllegalArgumentException, says 'the argument' and why, and gives the name of the"
            + " argument")
    void namesTheArgument() {
      var exception = new InvalidArgumentException("port", "must be between 1 and 65535");

      assertThat(exception)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("'port' must be between 1 and 65535");
      assertThat(exception.parameter()).isEqualTo("port");
    }

    @Test
    @DisplayName("needs the name of the argument and the reason")
    void needsTheNameAndTheReason() {
      assertThatNullPointerException()
          .isThrownBy(() -> new InvalidArgumentException(null, "x"))
          .withMessage("parameter");
      assertThatNullPointerException()
          .isThrownBy(() -> new InvalidArgumentException("port", null))
          .withMessage("reason");
    }

    @Test
    @DisplayName(
        "becomes an INVALID_ARGUMENT error with the same words, and the name of the argument as the"
            + " param detail")
    void becomesAServiceError() {
      var error =
          new InvalidArgumentException("port", "must be between 1 and 65535").toServiceError();

      assertThat(error.code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(error.message()).isEqualTo("'port' must be between 1 and 65535");
      assertThat(error.details()).containsOnlyKeys("param").containsEntry("param", "port");
    }
  }
}
