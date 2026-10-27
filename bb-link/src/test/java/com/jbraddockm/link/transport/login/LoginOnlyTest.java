package com.jbraddockm.link.transport.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.command.NoData;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Step;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link LoginOnly}, the command that only logs in. */
@DisplayName("LoginOnly: a command that logs in and asks for nothing")
class LoginOnlyTest {

  @Test
  @DisplayName("is a read-only command: it changes nothing on the device")
  void isReadOnly() {
    assertThat(new LoginOnly().readOnly()).isTrue();
  }

  @Test
  @DisplayName("finishes at once with no data, without sending a request")
  void finishesAtOnce() {
    assertThat(new LoginOnly().conversation().start())
        .isInstanceOfSatisfying(
            Step.Finish.class, finish -> assertThat(finish.result()).isSameAs(NoData.INSTANCE));
  }

  @Test
  @DisplayName("cannot be answered, since nothing was asked")
  void cannotBeAnswered() {
    var conversation = new LoginOnly().conversation();
    var answer = new CgiResponse(200, "text/xml", "", null);

    assertThatThrownBy(() -> conversation.onAnswer(answer, null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("nothing was asked, so nothing can be answered");
  }
}
