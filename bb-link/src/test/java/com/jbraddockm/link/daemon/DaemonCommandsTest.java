package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static com.jbraddockm.link.testing.JsonObjects.array;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.CertificateRetrieval;
import com.jbraddockm.link.endpoint.pairing.Pairing;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.ExchangeObserver;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Protocol-level tests for the commands on a connected session: PIN, properties, PIM and share
 * credentials.
 */
@DisplayName("The daemon: the commands on a session")
class DaemonCommandsTest {

  private FakeDevice device;
  private DaemonRun daemon;
  private String session;
  private long nextId = 100;

  @BeforeEach
  void connect() throws Exception {
    device = new FakeDevice("secret");
    daemon =
        new DaemonRun(
            new LinkService(
                TransportScheme.HTTP,
                _ -> {},
                ExchangeObserver.NONE,
                new Pairing(),
                new CertificateRetrieval()));
    daemon.hello();
    daemon.send(
        1,
        "request--connect.json",
        Map.of("port", String.valueOf(device.port()), "password", "secret"));
    assertThat(daemon.done(1).get("ok")).isEqualTo(true);
    session = daemon.session(1);
  }

  @AfterEach
  void stopEverything() throws Exception {
    daemon.close();
    device.close();
  }

  private long ask(String method) throws Exception {
    var id = nextId++;
    daemon.send(id, "request--session-command.json", Map.of("method", method, "session", session));
    daemon.done(id);
    return id;
  }

  private long ask(String resource, Map<String, String> values) throws Exception {
    var id = nextId++;
    var all = new LinkedHashMap<>(values);
    all.put("session", session);
    daemon.send(id, resource, all);
    daemon.done(id);
    return id;
  }

  @Nested
  @DisplayName("the reads")
  class Reads {

    @Test
    @DisplayName("getPin gives the PIN")
    void getPin() throws Exception {
      var id = ask("getPin");

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("pin", "2A3B4C5D");
    }

    @Test
    @DisplayName(
        "getDeviceProperties gives the properties, with the raw answer of the device when it is"
            + " asked for: one answer, and none by default")
    void getDeviceProperties() throws Exception {
      var plain = ask("getDeviceProperties");
      var raw = nextId++;
      daemon.send(
          raw,
          "request--session-command-raw.json",
          Map.of("method", "getDeviceProperties", "session", session));
      daemon.done(raw);

      assertThat(object(daemon.data(raw).get("device"))).containsEntry("name", "Test Z10");
      assertThat(daemon.events(plain, "result").getFirst())
          .as("no raw output by default")
          .doesNotContainKey("raw");
      assertThat(array(daemon.events(raw, "result").getFirst().get("raw")))
          .as("raw output on request: one answer")
          .hasSize(1);
    }
  }

  @Nested
  @DisplayName("the PIM program")
  class Pim {

    @Test
    @DisplayName(
        "listPimAccounts gives the command, the status of HTTP, the content type, the code and the"
            + " message, with the body as the JSON that it is, and no service")
    void listPimAccounts() throws Exception {
      var id = ask("listPimAccounts");

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      var listed = daemon.data(id);
      assertThat(listed)
          .containsEntry("command", "listAccounts")
          .containsEntry("httpStatus", 200L)
          .containsEntry("code", 0L)
          .containsEntry("message", "OK")
          .containsEntry("bodyFormat", "json");
      assertThat(String.valueOf(listed.get("contentType"))).startsWith("application/json");
      assertThat(listed.get("body"))
          .as("the body is the JSON itself, not a string")
          .isEqualTo(Json.parse(FakeDevice.DEFAULT_PIM_ANSWER));
      assertThat(listed).doesNotContainKey("service");
      assertThat(daemon.events(id, "log"))
          .anyMatch(event -> "Listing the PIM accounts".equals(event.get("message")));
    }

    @Test
    @DisplayName("findPimAccount gives the command that found it")
    void findPimAccount() throws Exception {
      var id = ask("request--find-pim-account.json", Map.of());

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("command", "findAccount");
    }

    @Test
    @DisplayName(
        "createPimAccount sends create_account with the workstation and its name, both encoded, and"
            + " reports the command that registered it")
    void createPimAccount() throws Exception {
      var id = ask("request--create-pim-account.json", Map.of());

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("command", "createAccount");
      assertThat(device.pimRequests().getLast())
          .contains("command=create_account")
          .contains("workstation_id=W1")
          .contains("workstation_name=My%20PC");
    }

    @Test
    @DisplayName("updatePimAccount sends update_account with the workstation and its name")
    void updatePimAccount() throws Exception {
      var id = ask("request--update-pim-account.json", Map.of());

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("command", "updateAccount");
      assertThat(device.pimRequests().getLast())
          .contains("command=update_account")
          .contains("workstation_id=W1")
          .contains("workstation_name=My%20PC");
    }

    @Test
    @DisplayName("deletePimAccount sends delete_account with the workstation")
    void deletePimAccount() throws Exception {
      var id = ask("request--delete-pim-account.json", Map.of());

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("command", "deleteAccount");
      assertThat(device.pimRequests().getLast())
          .contains("command=delete_account")
          .contains("workstation_id=W1");
    }

    @Test
    @DisplayName("setDefaultCalendar sends default on the calendars service with the object")
    void setDefaultCalendar() throws Exception {
      var id = ask("request--set-default-calendar.json", Map.of());

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id)).containsEntry("command", "setDefaultCalendar");
      assertThat(device.pimRequests().getLast())
          .contains("command=default")
          .contains("workstation_id=W1")
          .contains("service=calendars")
          .contains("object_id=C1");
    }

    @Test
    @DisplayName(
        "getPimItems with every filter asks the device the request of the protocol, and passes on a"
            + " body that is not JSON as text, saying so")
    void getPimItems() throws Exception {
      device.pimResponses.put(
          "get", Resources.text(DaemonCommandsTest.class, "pim--odd-text-answer.txt").strip());

      var id = ask("request--get-pim-items-filtered.json", Map.of("service", "contacts"));

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id))
          .containsEntry("service", "contacts")
          .containsEntry("bodyFormat", "text")
          .containsEntry(
              "body", Resources.text(DaemonCommandsTest.class, "pim--odd-text-answer.txt").strip());
      assertThat(device.pimRequests().getLast())
          .isEqualTo(Resources.text(DaemonCommandsTest.class, "pim--filtered-request.txt").strip());
    }

    @Test
    @DisplayName(
        "a read that the device refuses is not a success: COMMAND_FAILED with the code of the"
            + " device, and the result still arrives before the end, so that the body can be read")
    void aRefusedRead() throws Exception {
      device.pimResponses.put(
          "get", Resources.text(DaemonCommandsTest.class, "pim--refused-answer.json").strip());

      var id = ask("request--get-pim-items.json", Map.of("service", "events"));

      var done = daemon.done(id);
      assertThat(done.get("ok")).isEqualTo(false);
      assertThat(errorCode(done)).isEqualTo("COMMAND_FAILED");
      assertThat(errorDetails(done)).containsEntry("code", 1013L);
      assertThat(daemon.events(id, "*"))
          .extracting(event -> event.get("type"))
          .containsExactly("log", "result", "done");
      assertThat(String.valueOf(daemon.data(id).get("body"))).contains("received HTTP 401/403");
    }

    @Test
    @DisplayName("a read with no workstation is refused before anything is sent to the device")
    void noWorkstation() throws Exception {
      var before = device.pimRequests().size();

      var id = ask("findPimAccount");

      assertThat(errorCode(daemon.done(id))).isEqualTo("INVALID_ARGUMENT");
      assertThat(device.pimRequests()).hasSize(before);
    }

    @Test
    @DisplayName(
        "a service that is not one is refused, and the error lists the services that are accepted")
    void aServiceThatIsNotOne() throws Exception {
      var before = device.pimRequests().size();

      var id = ask("request--get-pim-items.json", Map.of("service", "photos"));

      var done = daemon.done(id);
      assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
      assertThat(errorMessage(done)).contains("must be one of: contacts, events, calendars");
      assertThat(device.pimRequests()).hasSize(before);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "request--get-pim-items-without-service.json",
          "request--get-pim-items-offset-without-local-only.json",
          "request--get-pim-items-limit-zero.json",
          "request--get-pim-items-limit-text.json",
          "request--get-pim-items-dirty-only-text.json"
        })
    @DisplayName(
        "an argument that is not right is refused before anything is sent to the device: no"
            + " service, an offset without localOnly, a limit of 0 or that is text, a flag that is"
            + " text")
    void argumentsAreRefusedFirst(String resource) throws Exception {
      var before = device.pimRequests().size();

      var id = ask(resource, Map.of());

      assertThat(errorCode(daemon.done(id))).isEqualTo("INVALID_ARGUMENT");
      assertThat(device.pimRequests()).hasSize(before);
    }
  }

  @Nested
  @DisplayName("the credentials of the share")
  class Share {

    @Test
    @DisplayName("getSmbCredentials gives the password of the latest login")
    void givesTheLatestPassword() throws Exception {
      var id = ask("getSmbCredentials");

      assertThat(daemon.done(id).get("ok")).isEqualTo(true);
      assertThat(daemon.data(id))
          .containsEntry("user", "dtm")
          .containsEntry("password", device.currentSharePassword());
    }

    @Test
    @DisplayName(
        "change when the session is renewed behind the scenes: the password is a new one, and"
            + " getSmbCredentials gives it")
    void changeWhenTheSessionIsRenewed() throws Exception {
      var before = String.valueOf(daemon.data(ask("getSmbCredentials")).get("password"));

      device.expireSessions();
      assertThat(daemon.done(ask("getPin")).get("ok"))
          .as("the session is renewed without the client knowing")
          .isEqualTo(true);
      var after = String.valueOf(daemon.data(ask("getSmbCredentials")).get("password"));

      assertThat(after).isNotEqualTo(before).isEqualTo(device.currentSharePassword());
    }
  }
}
