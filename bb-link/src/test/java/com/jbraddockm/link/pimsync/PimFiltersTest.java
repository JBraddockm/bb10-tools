package com.jbraddockm.link.pimsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link PimFilters}. */
@DisplayName("PimFilters: what the items may be narrowed by")
class PimFiltersTest {

  private static void assertRefused(ThrowingCall call, String parameter, String reason) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
        .hasMessageContaining(reason);
  }

  @FunctionalInterface
  private interface ThrowingCall {
    void run();
  }

  @Test
  @DisplayName("none is no filter at all")
  void noneIsNoFilter() {
    var none = PimFilters.none();

    assertThat(none.updatedTime()).isNull();
    assertThat(none.limit()).isEmpty();
    assertThat(none.dirtyOnly()).isFalse();
    assertThat(none.localOnly()).isFalse();
    assertThat(none.offset()).isEmpty();
  }

  @ParameterizedTest(name = "\"{0}\"")
  @ValueSource(strings = {"", " ", "\t"})
  @DisplayName("refuses a time that is blank, naming updatedTime")
  void refusesABlankTime(String time) {
    assertRefused(
        () -> new PimFilters(time, OptionalInt.empty(), false, false, OptionalInt.empty()),
        "updatedTime",
        "must not be blank");
  }

  @ParameterizedTest(name = "limit {0}")
  @ValueSource(ints = {0, -1, -100})
  @DisplayName("refuses a limit under 1, naming limit")
  void refusesALimitUnderOne(int limit) {
    assertRefused(
        () -> new PimFilters(null, OptionalInt.of(limit), false, false, OptionalInt.empty()),
        "limit",
        "at least 1");
  }

  @Test
  @DisplayName("takes a limit of 1")
  void takesALimitOfOne() {
    assertThat(new PimFilters(null, OptionalInt.of(1), false, false, OptionalInt.empty()).limit())
        .hasValue(1);
  }

  @Test
  @DisplayName("refuses an offset that is negative, naming offset")
  void refusesANegativeOffset() {
    assertRefused(
        () -> new PimFilters(null, OptionalInt.empty(), false, true, OptionalInt.of(-1)),
        "offset",
        "must not be negative");
  }

  @Test
  @DisplayName(
      "refuses an offset without localOnly, since the device only takes one into what is local")
  void refusesAnOffsetWithoutLocalOnly() {
    assertRefused(
        () -> new PimFilters(null, OptionalInt.empty(), false, false, OptionalInt.of(5)),
        "offset",
        "only applies together with localOnly");
  }

  @Test
  @DisplayName("takes an offset of 0 with localOnly")
  void takesAnOffsetOfZero() {
    assertThat(new PimFilters(null, OptionalInt.empty(), false, true, OptionalInt.of(0)).offset())
        .hasValue(0);
  }

  @Test
  @DisplayName(
      "accepts null for the time (meaning no filter), but requires the two OptionalInt values")
  void requiresTheTwoOptionalInts() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PimFilters(null, null, false, false, OptionalInt.empty()))
        .withMessage("limit");
    assertThatNullPointerException()
        .isThrownBy(() -> new PimFilters(null, OptionalInt.empty(), false, false, null))
        .withMessage("offset");
  }
}
