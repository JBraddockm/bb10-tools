package com.jbraddockm.link.cgi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link UploadObserver}. */
@DisplayName("UploadObserver: told how much of a file has gone")
class UploadObserverTest {

  @Test
  @DisplayName("NONE takes any number of bytes and does nothing")
  void noneDoesNothing() {
    assertThatCode(
            () -> {
              UploadObserver.NONE.sent(0);
              UploadObserver.NONE.sent(Long.MAX_VALUE);
            })
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("an observer can be a lambda, and is told each count as it comes")
  void anObserverCanBeALambda() {
    var told = new ArrayList<Long>();
    UploadObserver observer = told::add;

    observer.sent(10);
    observer.sent(20);

    assertThat(told).isEqualTo(List.of(10L, 20L));
  }
}
