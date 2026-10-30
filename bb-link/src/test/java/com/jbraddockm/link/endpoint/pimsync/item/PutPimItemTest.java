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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for PutPimItem: the POST that writes one item, carrying it as a one-element JSON array (the
 * device's write endpoint is a batch that expects an array).
 */
@DisplayName("PutPimItem: the request that writes one item")
class PutPimItemTest {

  private static CgiRequest.Form requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Form) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events", "CALENDARS, calendars"})
  @DisplayName("posts put with the workstation, the service and the item shaped for the service")
  void postsTheItem(PimService service, String name) {
    Map<String, Object> item = Map.of("title", "Lunch");

    var request = requestOf(new PutPimItem("WS1", service, item));

    // contacts/events are batch endpoints (a JSON array); a calendar is a single object.
    var expected = service == PimService.CALENDARS ? Json.write(item) : Json.write(List.of(item));
    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "put")
        .containsEntry(FormField.SERVICE, name)
        .containsEntry(FormField.WORKSTATION_ID, "WS1")
        .containsEntry(FormField.DATA, expected);
    assertThat(request.describe()).isEqualTo("Writing one " + name + " item");
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS", "EVENTS"})
  @DisplayName("wraps the item in a one-element array for the batch endpoints (contacts, events)")
  void wrapsBatchItemInAnArray(PimService service) {
    Map<String, Object> item = Map.of("note", "a & b");

    var request = requestOf(new PutPimItem("WS1", service, item));

    assertThat(request.fields().get(FormField.DATA)).isEqualTo(Json.write(List.of(item)));
    assertThat(request.fields().get(FormField.DATA)).startsWith("[").endsWith("]");
  }

  @Test
  @DisplayName("sends a calendar as a bare object, because that endpoint is not a batch")
  void sendsCalendarAsBareObject() {
    Map<String, Object> item = Map.of("name", "Work");

    var request = requestOf(new PutPimItem("WS1", PimService.CALENDARS, item));

    assertThat(request.fields().get(FormField.DATA)).isEqualTo(Json.write(item));
    assertThat(request.fields().get(FormField.DATA)).startsWith("{").endsWith("}");
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
