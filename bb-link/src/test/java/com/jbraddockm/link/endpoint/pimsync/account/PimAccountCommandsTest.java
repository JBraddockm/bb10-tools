package com.jbraddockm.link.endpoint.pimsync.account;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the account-level PIM commands: the request each one sends to the PIM program. */
@DisplayName("ListPimAccounts, FindPimAccount, CreatePimAccount: the account requests")
class PimAccountCommandsTest {

  private static CgiRequest.Get requestOf(DeviceCommand<PimAnswer> command) {
    return (CgiRequest.Get) sent(command.conversation().start());
  }

  @Nested
  @DisplayName("ListPimAccounts")
  class Listing {

    @Test
    @DisplayName("asks for the command list_accounts, and nothing else")
    void asksForTheAccounts() {
      var request = requestOf(new ListPimAccounts());

      assertThat(request.pathAndQuery()).isEqualTo("/cgi-bin/pimsync.cgi?command=list_accounts");
      assertThat(request.describe()).isEqualTo("Listing the PIM accounts");
    }

    @Test
    @DisplayName("only reads")
    void onlyReads() {
      assertThat(new ListPimAccounts().readOnly()).isTrue();
    }
  }

  @Nested
  @DisplayName("FindPimAccount")
  class Finding {

    @Test
    @DisplayName("asks for the command find_accountId with the workstation, whose name is encoded")
    void asksForTheAccountOfAWorkstation() {
      assertThat(requestOf(new FindPimAccount("WS1")).pathAndQuery())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=find_accountId&workstation_id=WS1");
      assertThat(requestOf(new FindPimAccount("WS 1&x")).pathAndQuery())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=find_accountId&workstation_id=WS%201%26x");
    }

    @Test
    @DisplayName("says what it looks up, and only reads")
    void saysWhatItDoes() {
      assertThat(requestOf(new FindPimAccount("WS1")).describe())
          .isEqualTo("Looking up the PIM account of a workstation");
      assertThat(new FindPimAccount("WS1").readOnly()).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is not a workstation")
    @NullAndEmptySource
    @ValueSource(strings = {"  ", "\t"})
    @DisplayName("needs a workstation that is not blank, naming workstationId")
    void needsAWorkstation(String workstation) {
      assertThatThrownBy(() -> new FindPimAccount(workstation))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
    }
  }

  @Nested
  @DisplayName("CreatePimAccount")
  class Creating {

    @Test
    @DisplayName("asks for create_account with the workstation and its name, both encoded")
    void asksToRegister() {
      var request = requestOf(new CreatePimAccount("WS1", "My PC"));

      assertThat(request.pathAndQuery())
          .isEqualTo(
              "/cgi-bin/pimsync.cgi?command=create_account&workstation_id=WS1"
                  + "&workstation_name=My%20PC");
      assertThat(request.describe()).isEqualTo("Registering the workstation as a PIM account");
    }

    @Test
    @DisplayName("changes the device, so it is not read-only")
    void changesTheDevice() {
      assertThat(new CreatePimAccount("WS1", "My PC").readOnly()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not a workstation")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs a workstation that is not blank, naming workstationId")
    void needsAWorkstation(String workstation) {
      assertThatThrownBy(() -> new CreatePimAccount(workstation, "My PC"))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
    }

    @ParameterizedTest(name = "\"{0}\" is not a name")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs a name that is not blank, naming workstationName")
    void needsAName(String name) {
      assertThatThrownBy(() -> new CreatePimAccount("WS1", name))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationName"));
    }
  }

  @Nested
  @DisplayName("UpdatePimAccount")
  class Updating {

    @Test
    @DisplayName("asks for update_account with the workstation and its name, both encoded")
    void asksToUpdate() {
      var request = requestOf(new UpdatePimAccount("WS1", "My PC"));

      assertThat(request.pathAndQuery())
          .isEqualTo(
              "/cgi-bin/pimsync.cgi?command=update_account&workstation_id=WS1"
                  + "&workstation_name=My%20PC");
      assertThat(request.describe()).isEqualTo("Updating the PIM account of a workstation");
    }

    @Test
    @DisplayName("changes the device, so it is not read-only")
    void changesTheDevice() {
      assertThat(new UpdatePimAccount("WS1", "My PC").readOnly()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not a name")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs a name that is not blank, naming workstationName")
    void needsAName(String name) {
      assertThatThrownBy(() -> new UpdatePimAccount("WS1", name))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationName"));
    }
  }

  @Nested
  @DisplayName("DeletePimAccount")
  class Deleting {

    @Test
    @DisplayName("asks for delete_account with the workstation")
    void asksToDelete() {
      var request = requestOf(new DeletePimAccount("WS1"));

      assertThat(request.pathAndQuery())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=delete_account&workstation_id=WS1");
      assertThat(request.describe()).isEqualTo("Removing the PIM account of a workstation");
    }

    @Test
    @DisplayName("changes the device, so it is not read-only")
    void changesTheDevice() {
      assertThat(new DeletePimAccount("WS1").readOnly()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not a workstation")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs a workstation that is not blank, naming workstationId")
    void needsAWorkstation(String workstation) {
      assertThatThrownBy(() -> new DeletePimAccount(workstation))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
    }
  }

  @Nested
  @DisplayName("SetDefaultCalendar")
  class SettingDefault {

    @Test
    @DisplayName("asks for default on the calendars service with the workstation and the object")
    void asksToSetDefault() {
      var request = requestOf(new SetDefaultCalendar("WS1", "C1"));

      assertThat(request.pathAndQuery())
          .isEqualTo(
              "/cgi-bin/pimsync.cgi?command=default&workstation_id=WS1&service=calendars"
                  + "&object_id=C1");
      assertThat(request.describe()).isEqualTo("Setting the default calendar of a workstation");
    }

    @Test
    @DisplayName("changes the device, so it is not read-only")
    void changesTheDevice() {
      assertThat(new SetDefaultCalendar("WS1", "C1").readOnly()).isFalse();
    }

    @ParameterizedTest(name = "\"{0}\" is not an object")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs an object that is not blank, naming objectId")
    void needsAnObject(String object) {
      assertThatThrownBy(() -> new SetDefaultCalendar("WS1", object))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("objectId"));
    }
  }
}
