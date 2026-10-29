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

/**
 * Tests for PutPimItem: the POST that writes one item, carrying the item as a verbatim JSON body.
 */
@DisplayName("PutPimItem: the request that writes one item")
class PutPimItemTest {

  private static CgiRequest.Form requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Form) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events", "CALENDARS, calendars"})
  @DisplayName("posts put with the workstation, the service and the item as the json field")
  void postsTheItem(PimService service, String name) {
    Map<String, Object> item = Map.of("title", "Lunch");

    var request = requestOf(new PutPimItem("WS1", service, item));

    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "put")
        .containsEntry(FormField.SERVICE, name)
        .containsEntry(FormField.WORKSTATION_ID, "WS1")
        .containsEntry(FormField.JSON, Json.write(item));
    assertThat(request.describe()).isEqualTo("Writing one " + name + " item");
  }

  @Test
  @DisplayName("forwards the item verbatim, and the encoded body carries it percent-encoded")
  void forwardsTheItemVerbatim() {
    Map<String, Object> item = Map.of("note", "a & b");

    var request = requestOf(new PutPimItem("WS1", PimService.CONTACTS, item));

    assertThat(request.fields().get(FormField.JSON)).isEqualTo(Json.write(item));
    assertThat(request.encodedBody())
        .startsWith("command=put&service=contacts&workstation_id=WS1&json=");
  }

  @Test
  @DisplayName("changes the device, so it is not read-only")
  void changesTheDevice() {
    assertThat(new PutPimItem("WS1", PimService.CONTACTS, Map.of("a", "b")).readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new PutPimItem(workstation, PimService.CONTACTS, Map.of("a", "b")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @Test
  @DisplayName("refuses an empty item, naming item")
  void refusesAnEmptyItem() {
    assertThatThrownBy(() -> new PutPimItem("WS1", PimService.CONTACTS, Map.of()))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("item"));
  }

  @Test
  @DisplayName("needs a service and an item")
  void needsAServiceAndItem() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PutPimItem("WS1", null, Map.of("a", "b")))
        .withMessage("service");
    assertThatNullPointerException()
        .isThrownBy(() -> new PutPimItem("WS1", PimService.CONTACTS, null))
        .withMessage("item");
  }
}
