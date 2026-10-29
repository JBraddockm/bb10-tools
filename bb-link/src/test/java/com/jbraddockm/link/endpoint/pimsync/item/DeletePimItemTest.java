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

/** Tests for DeletePimItem: the request that removes one item of a service. */
@DisplayName("DeletePimItem: the request that removes one item")
class DeletePimItemTest {

  private static CgiRequest.Get requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Get) sent(command.conversation().start());
  }

  @ParameterizedTest(name = "{0}")
  @CsvSource({"CONTACTS, contacts", "EVENTS, events", "CALENDARS, calendars"})
  @DisplayName("asks for delete with the workstation, the service and the object, and says which")
  void asksToDelete(PimService service, String name) {
    var request = requestOf(new DeletePimItem("WS1", service, "OBJ1"));

    assertThat(request.pathAndQuery())
        .isEqualTo(
            "/cgi-bin/pimsync.cgi?command=delete&workstation_id=WS1&service="
                + name
                + "&object_id=OBJ1");
    assertThat(request.describe()).isEqualTo("Removing one " + name + " item");
  }

  @Test
  @DisplayName("encodes the object id, and changes the device so it is not read-only")
  void encodesAndWrites() {
    var command = new DeletePimItem("WS1", PimService.CONTACTS, "O 1&x");

    assertThat(requestOf(command).pathAndQuery()).contains("object_id=O%201%26x");
    assertThat(command.readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new DeletePimItem(workstation, PimService.CONTACTS, "OBJ1"))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not an object")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs an object that is not blank, naming objectId")
  void needsAnObject(String object) {
    assertThatThrownBy(() -> new DeletePimItem("WS1", PimService.CONTACTS, object))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("objectId"));
  }

  @Test
  @DisplayName("needs a service")
  void needsAService() {
    assertThatNullPointerException()
        .isThrownBy(() -> new DeletePimItem("WS1", null, "OBJ1"))
        .withMessage("service");
  }
}
