package com.jbraddockm.link.endpoint.pimsync.item;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for SummarizePimSync: the GET that opens a sync round. */
@DisplayName("SummarizePimSync: the request that opens a sync round")
class SummarizePimSyncTest {

  private static CgiRequest.Get requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Get) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events"})
  @DisplayName("asks for summary with the workstation and the service, and says which")
  void asksToSummarize(PimService service, String name) {
    var request = requestOf(new SummarizePimSync("WS1", service));

    assertThat(request.pathAndQuery())
        .isEqualTo("/cgi-bin/pimsync.cgi?command=summary&workstation_id=WS1&service=" + name);
    assertThat(request.describe()).isEqualTo("Opening a " + name + " sync round");
  }

  @Test
  @DisplayName("is part of a sync round, so it is not read-only")
  void isNotReadOnly() {
    assertThat(new SummarizePimSync("WS1", PimService.CONTACTS).readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new SummarizePimSync(workstation, PimService.CONTACTS))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @Test
  @DisplayName("refuses calendars, which no sync round covers, naming service")
  void refusesCalendars() {
    assertThatThrownBy(() -> new SummarizePimSync("WS1", PimService.CALENDARS))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("service"));
  }

  @Test
  @DisplayName("needs a service")
  void needsAService() {
    assertThatNullPointerException()
        .isThrownBy(() -> new SummarizePimSync("WS1", null))
        .withMessage("service");
  }
}
