package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static com.jbraddockm.link.testing.JsonObjects.array;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.pairing.CertificateRetrieval;
import com.jbraddockm.link.pairing.Pairing;
import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.update.InstallPackages;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Protocol-level tests for {@code uninstallPackages}. */
@DisplayName("The daemon: uninstallPackages")
class DaemonUninstallTest {

  private static final long CONNECT = 1;

  private FakeDevice device;
  private DaemonRun daemon;
  private String session;

  @BeforeEach
  void connect() throws Exception {
    device = new FakeDevice("secret");
    device.propertiesXml =
        Resources.text(InstallPackages.class, "session--uninstall-properties.xml");
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
        CONNECT,
        "request--connect.json",
        Map.of("port", String.valueOf(device.port()), "password", "secret"));
    assertThat(daemon.done(CONNECT).get("ok")).isEqualTo(true);
    session = daemon.session(CONNECT);
  }

  @AfterEach
  void stopEverything() throws Exception {
    daemon.close();
    device.close();
  }

  private Map<String, Object> remove(long id, String resource, Map<String, String> values)
      throws Exception {
    var all = new LinkedHashMap<>(values);
    all.put("session", session);
    daemon.send(id, resource, all);
    return daemon.done(id);
  }

  @Test
  @DisplayName(
      "removes a package: gives it as removed, with the last answer of the device as JSON, and"
          + " reports progress that names the phase and the package")
  void removesAPackage() throws Exception {
    var done = remove(310, "request--uninstall-packages.json", Map.of("packageId", "idFirst"));

    assertThat(done.get("ok")).isEqualTo(true);
    var data = daemon.data(310);
    assertThat(array(data.get("removed"))).containsExactly("idFirst");
    assertThat(data).containsEntry("bodyFormat", "xml");
    assertThat(daemon.events(310, "progress"))
        .isNotEmpty()
        .allMatch(
            event -> "removing".equals(event.get("phase")) && "idFirst".equals(event.get("item")));
  }

  @Test
  @DisplayName("says what it does in the log")
  void logsWhatItDoes() throws Exception {
    remove(310, "request--uninstall-packages.json", Map.of("packageId", "idFirst"));

    assertThat(daemon.events(310, "log"))
        .anyMatch(event -> "Removing package 1 of 1".equals(event.get("message")));
  }

  @Test
  @DisplayName("needs the ids, as INVALID_ARGUMENT naming packageIds")
  void needsTheIds() throws Exception {
    var done = remove(311, "request--uninstall-packages-without-ids.json", Map.of());

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(done)).containsEntry("param", "packageIds");
  }

  @Test
  @DisplayName("refuses an id that is not an id, naming its position, and the same package twice")
  void refusesBadIds() throws Exception {
    var bad = remove(312, "request--uninstall-packages-bad-id.json", Map.of());
    var twice =
        remove(
            313,
            "request--uninstall-packages-two.json",
            Map.of("first", "idFirst", "second", "idFirst"));

    assertThat(errorCode(bad)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(bad)).containsEntry("param", "packageIds[0]");
    assertThat(errorCode(twice)).isEqualTo("INVALID_ARGUMENT");
  }

  @Test
  @DisplayName("refuses a check that is not a boolean, naming check")
  void checkMustBeABoolean() throws Exception {
    var done =
        remove(314, "request--uninstall-packages-check-no.json", Map.of("packageId", "idSecond"));

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(done)).containsEntry("param", "check");
  }

  @Test
  @DisplayName(
      "refuses an id that is not installed, naming its position, and firmware, and sends nothing to"
          + " remove")
  void refusesWhatIsNotInstalledOrIsFirmware() throws Exception {
    var ghost =
        remove(
            315,
            "request--uninstall-packages-two.json",
            Map.of("first", "idSecond", "second", "ghost"));
    var firmware = remove(316, "request--uninstall-packages.json", Map.of("packageId", "idOs"));

    assertThat(errorCode(ghost)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(ghost)).containsEntry("param", "packageIds[1]");
    assertThat(errorCode(firmware)).isEqualTo("INVALID_ARGUMENT");
    assertThat(device.updateRequests).isEmpty();
  }

  @Test
  @DisplayName(
      "with the check off, shows what the device refuses: COMMAND_FAILED in the device's words, the"
          + " result sent first, and nothing removed")
  void aDeviceThatRefusesWhenTheCheckIsOff() throws Exception {
    device.updateUnremovable.add("ghost");

    var done =
        remove(317, "request--uninstall-packages-check-false.json", Map.of("packageId", "ghost"));

    assertThat(done.get("ok")).isEqualTo(false);
    assertThat(errorCode(done)).isEqualTo("COMMAND_FAILED");
    assertThat(errorMessage(done)).isEqualTo("Cannot uninstall packageid");
    assertThat(daemon.events(317, "result")).hasSize(1);
    assertThat(array(daemon.data(317).get("removed"))).isEmpty();
  }
}
