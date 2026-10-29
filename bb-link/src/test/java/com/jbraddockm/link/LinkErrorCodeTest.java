package com.jbraddockm.link;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Tests for {@link LinkErrorCode}. */
@DisplayName("LinkErrorCode: the errors of the tool for BlackBerry devices")
class LinkErrorCodeTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(LinkErrorCode.class)
  @DisplayName("each is written as its own name")
  void eachIsWrittenAsItsName(LinkErrorCode code) {
    assertThat(code.wireName()).isEqualTo(code.name());
  }

  @Test
  @DisplayName("are nine, and are errors of the protocol")
  void areNine() {
    assertThat(LinkErrorCode.values()).hasSize(9).allMatch(ErrorCode.class::isInstance);
  }

  @Test
  @DisplayName(
      "have no name that an error of the core has, so that a client never takes one for the other")
  void noNameIsShared() {
    var core = Arrays.stream(CoreErrorCode.values()).map(CoreErrorCode::wireName).toList();

    assertThat(Arrays.stream(LinkErrorCode.values()).map(LinkErrorCode::wireName))
        .doesNotContainAnyElementsOf(core);
  }
}
