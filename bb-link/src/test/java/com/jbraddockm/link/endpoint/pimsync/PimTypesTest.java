package com.jbraddockm.link.endpoint.pimsync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for the PIM services, commands and answer types. */
@DisplayName("PimService, PimCommand and PimAnswer: the vocabulary of the PIM program")
class PimTypesTest {

  @Nested
  @DisplayName("PimService")
  class Service {

    @ParameterizedTest(name = "{0} is \"{1}\"")
    @CsvSource({"CONTACTS, contacts", "EVENTS, events", "CALENDARS, calendars"})
    @DisplayName("each service has the name that the device uses, and of finds it by that name")
    void eachServiceHasItsName(PimService service, String name) {
      assertThat(service.wireName()).isEqualTo(name);
      assertThat(PimService.of(name)).isEqualTo(service);
    }

    @ParameterizedTest(name = "\"{0}\" is not a service")
    @NullAndEmptySource
    @ValueSource(strings = {"Contacts", "CONTACTS", "tasks", "contacts "})
    @DisplayName(
        "of refuses a name that is not exactly one of them, naming service and listing the others")
    void ofRefusesOtherNames(String name) {
      assertThatThrownBy(() -> PimService.of(name))
          .isInstanceOfSatisfying(
              InvalidArgumentException.class,
              invalid -> assertThat(invalid.parameter()).isEqualTo("service"))
          .hasMessageContaining("contacts, events, calendars");
    }
  }

  @Nested
  @DisplayName("PimCommand")
  class Command {

    @ParameterizedTest(name = "{0}: \"{1}\" on the wire, \"{2}\" in JSON")
    @CsvSource({
      "LIST_ACCOUNTS, list_accounts, listAccounts",
      "FIND_ACCOUNT_ID, find_accountId, findAccount",
      "GET, get, get",
      "CREATE_ACCOUNT, create_account, createAccount",
      "UPDATE_ACCOUNT, update_account, updateAccount",
      "DELETE_ACCOUNT, delete_account, deleteAccount",
      "DEFAULT, default, setDefaultCalendar"
    })
    @DisplayName(
        "each command has the name that the device is asked with, and the name that the daemon"
            + " answers with")
    void eachCommandHasItsNames(PimCommand command, String wire, String json) {
      assertThat(command.wireName()).isEqualTo(wire);
      assertThat(command.jsonName()).isEqualTo(json);
    }
  }

  @Nested
  @DisplayName("PimAnswer")
  class Answer {

    private static PimAnswer answer(PimCommand command, PimService service, String resource) {
      var body = Resources.text(PimTypesTest.class, resource);
      return new PimAnswer(
          command, service, 200, "application/json", 0, "OK", ResponseBody.of(body));
    }

    private static String expected(String resource) {
      return Json.write(Json.parse(Resources.text(PimTypesTest.class, resource)));
    }

    @Test
    @DisplayName("gives as JSON what it was, with no service for a command that has none")
    void givesTheExpectedJson() {
      assertThat(Json.write(answer(PimCommand.LIST_ACCOUNTS, null, "pim--accounts.json").toJson()))
          .isEqualTo(expected("pim-answer--accounts--expected.json"));
    }

    @Test
    @DisplayName("gives the service by its name for a command that has one")
    void givesTheService() {
      assertThat(Json.write(answer(PimCommand.GET, PimService.CONTACTS, "pim--ok.json").toJson()))
          .isEqualTo(expected("pim-answer--contacts--expected.json"));
    }
  }
}
