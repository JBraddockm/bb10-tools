package com.jbraddockm.link.endpoint.pimsync.item;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.FormField;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for FinishPimSync: the POST that closes a sync round with its reconciled payload. */
@DisplayName("FinishPimSync: the request that closes a sync round")
class FinishPimSyncTest {

  private static CgiRequest.Form requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Form) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events"})
  @DisplayName("posts done with the workstation, the service and the payload as the data field")
  void postsTheRound(PimService service, String name) {
    Map<String, Object> payload = Map.of("ack", "1");

    var request = requestOf(new FinishPimSync("WS1", service, payload));

    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "done")
        .containsEntry(FormField.SERVICE, name)
        .containsEntry(FormField.WORKSTATION_ID, "WS1")
        .containsEntry(FormField.DATA, Json.write(payload));
    assertThat(request.describe()).isEqualTo("Closing a " + name + " sync round");
  }

  @Test
  @DisplayName("forwards the payload verbatim, and the encoded body carries it")
  void forwardsThePayload() {
    Map<String, Object> payload = Map.of("note", "a & b");

    var request = requestOf(new FinishPimSync("WS1", PimService.EVENTS, payload));

    assertThat(request.fields().get(FormField.DATA)).isEqualTo(Json.write(payload));
    assertThat(request.encodedBody())
        .startsWith("command=done&service=events&workstation_id=WS1&data=");
  }

  @Test
  @DisplayName("changes the device, so it is not read-only")
  void changesTheDevice() {
    assertThat(new FinishPimSync("WS1", PimService.CONTACTS, Map.of("a", "b")).readOnly())
        .isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new FinishPimSync(workstation, PimService.CONTACTS, Map.of("a", "b")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @Test
  @DisplayName("refuses calendars, which no sync round covers, naming service")
  void refusesCalendars() {
    assertThatThrownBy(() -> new FinishPimSync("WS1", PimService.CALENDARS, Map.of("a", "b")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("service"));
  }

  @Test
  @DisplayName("refuses an empty payload, naming payload")
  void refusesAnEmptyPayload() {
    assertThatThrownBy(() -> new FinishPimSync("WS1", PimService.CONTACTS, Map.of()))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("payload"));
  }

  @Test
  @DisplayName("needs a service and a payload")
  void needsAServiceAndPayload() {
    assertThatNullPointerException()
        .isThrownBy(() -> new FinishPimSync("WS1", null, Map.of("a", "b")))
        .withMessage("service");
    assertThatNullPointerException()
        .isThrownBy(() -> new FinishPimSync("WS1", PimService.CONTACTS, null))
        .withMessage("payload");
  }
}
