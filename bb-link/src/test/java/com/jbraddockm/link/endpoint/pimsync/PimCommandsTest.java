package com.jbraddockm.link.endpoint.pimsync;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import java.util.OptionalInt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the PIM commands: the request each one sends, and what each makes of the answer. */
@DisplayName("ListPimAccounts, FindPimAccount, GetPimItems: the requests for the PIM program")
class PimCommandsTest {

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
  @DisplayName("GetPimItems")
  class Getting {

    private static GetPimItems items(PimService service, PimFilters filters) {
      return new GetPimItems("WS1", service, filters);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({"CONTACTS, contacts", "EVENTS, events", "CALENDARS, calendars"})
    @DisplayName(
        "asks for the command get with the workstation and the service, and says which it reads")
    void asksForAService(PimService service, String name) {
      var request = requestOf(items(service, PimFilters.none()));

      assertThat(request.pathAndQuery())
          .isEqualTo("/cgi-bin/pimsync.cgi?command=get&workstation_id=WS1&service=" + name);
      assertThat(request.describe()).isEqualTo("Reading the " + name);
    }

    @Test
    @DisplayName("adds each filter that is given, and only that one")
    void addsEachFilterAlone() {
      var base = "/cgi-bin/pimsync.cgi?command=get&workstation_id=WS1&service=events";

      assertThat(
              requestOf(
                      items(
                          PimService.EVENTS,
                          new PimFilters(
                              "2026-10-15T09:00:00Z",
                              OptionalInt.empty(),
                              false,
                              false,
                              OptionalInt.empty())))
                  .pathAndQuery())
          .isEqualTo(base + "&updated_time=2026-10-15T09%3A00%3A00Z");
      assertThat(
              requestOf(
                      items(
                          PimService.EVENTS,
                          new PimFilters(
                              null, OptionalInt.of(50), false, false, OptionalInt.empty())))
                  .pathAndQuery())
          .isEqualTo(base + "&limit=50");
      assertThat(
              requestOf(
                      items(
                          PimService.EVENTS,
                          new PimFilters(
                              null, OptionalInt.empty(), true, false, OptionalInt.empty())))
                  .pathAndQuery())
          .isEqualTo(base + "&dirty_only=true");
      assertThat(
              requestOf(
                      items(
                          PimService.EVENTS,
                          new PimFilters(
                              null, OptionalInt.empty(), false, true, OptionalInt.empty())))
                  .pathAndQuery())
          .isEqualTo(base + "&local_only=true");
      assertThat(
              requestOf(
                      items(
                          PimService.EVENTS,
                          new PimFilters(
                              null, OptionalInt.empty(), false, true, OptionalInt.of(10))))
                  .pathAndQuery())
          .isEqualTo(base + "&local_only=true&offset=10");
    }

    @Test
    @DisplayName(
        "adds all the filters in the order of the protocol: time, limit, dirty only, local only,"
            + " offset")
    void addsAllTheFiltersInOrder() {
      var filters =
          new PimFilters(
              "2026-10-15T09:00:00Z", OptionalInt.of(50), true, true, OptionalInt.of(10));

      assertThat(requestOf(items(PimService.EVENTS, filters)).pathAndQuery())
          .isEqualTo(
              "/cgi-bin/pimsync.cgi?command=get&workstation_id=WS1&service=events"
                  + "&updated_time=2026-10-15T09%3A00%3A00Z&limit=50&dirty_only=true&local_only=true&offset=10");
    }

    @Test
    @DisplayName("has no filter for a flag that is false, and no offset of 0 is dropped")
    void anOffsetOfZeroIsKept() {
      var filters = new PimFilters(null, OptionalInt.empty(), false, true, OptionalInt.of(0));

      assertThat(requestOf(items(PimService.CONTACTS, filters)).pathAndQuery())
          .endsWith("&local_only=true&offset=0");
    }

    @Test
    @DisplayName("encodes the workstation, and only reads")
    void encodesTheWorkstation() {
      var command = new GetPimItems("WS 1&x", PimService.CONTACTS, PimFilters.none());

      assertThat(requestOf(command).pathAndQuery()).contains("workstation_id=WS%201%26x");
      assertThat(command.readOnly()).isTrue();
    }

    @ParameterizedTest(name = "\"{0}\" is not a workstation")
    @NullAndEmptySource
    @ValueSource(strings = {" "})
    @DisplayName("needs a workstation that is not blank, naming workstationId")
    void needsAWorkstation(String workstation) {
      assertThatThrownBy(() -> new GetPimItems(workstation, PimService.CONTACTS, PimFilters.none()))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
    }

    @Test
    @DisplayName("needs a service and filters")
    void needsAServiceAndFilters() {
      assertThatNullPointerException()
          .isThrownBy(() -> new GetPimItems("WS1", null, PimFilters.none()))
          .withMessage("service");
      assertThatNullPointerException()
          .isThrownBy(() -> new GetPimItems("WS1", PimService.CONTACTS, null))
          .withMessage("filters");
    }
  }
}
