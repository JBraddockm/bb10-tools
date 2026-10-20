package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static com.jbraddockm.link.testing.JsonObjects.array;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.update.InstallPackages;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Protocol-level tests for {@code installPackages}. */
@DisplayName("The daemon: installPackages")
class DaemonInstallTest {

  private static final long CONNECT = 1;

  @TempDir private Path folder;
  private FakeDevice device;
  private DaemonRun daemon;
  private String session;

  @BeforeEach
  void connect() throws Exception {
    device = new FakeDevice("secret");
    daemon = new DaemonRun(new LinkService(TransportScheme.HTTP, _ -> {}, ExchangeObserver.NONE));
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

  private Path bar(String name) {
    return Resources.copyTo(InstallPackages.class, name, folder);
  }

  private Map<String, Object> install(long id, String resource, Map<String, String> values)
      throws Exception {
    var all = new LinkedHashMap<>(values);
    all.put("session", session);
    daemon.send(id, resource, all);
    return daemon.done(id);
  }

  @Test
  @DisplayName(
      "installs a package: gives it as installed, with the last answer of the device as JSON, and"
          + " reports progress named installing")
  void installsAPackage() throws Exception {
    device.updateProgress = List.of(50, 100);

    var done =
        install(
            300,
            "request--install-packages.json",
            Map.of("file", bar("application--first.bar").toString()));

    assertThat(done.get("ok")).isEqualTo(true);
    var data = daemon.data(300);
    var installed = array(data.get("installed"));
    assertThat(installed).hasSize(1);
    assertThat(object(installed.getFirst())).containsEntry("packageId", "idFirst");
    assertThat(data).containsEntry("bodyFormat", "xml");
    assertThat(daemon.events(300, "progress"))
        .isNotEmpty()
        .anyMatch(event -> "installing".equals(event.get("phase")));
  }

  @Test
  @DisplayName(
      "says what it does in the log, and not which program of the device it talks to: it starts,"
          + " sends and ends")
  void logsWhatItDoes() throws Exception {
    device.updateProgress = List.of(50, 100);

    install(
        300,
        "request--install-packages.json",
        Map.of("file", bar("application--first.bar").toString()));

    var messages =
        daemon.events(300, "log").stream()
            .map(event -> String.valueOf(event.get("message")))
            .toList();
    assertThat(messages).contains("Starting the installation").hasSizeGreaterThanOrEqualTo(4);
    assertThat(messages).noneMatch(message -> message.contains(".cgi"));
  }

  @Test
  @DisplayName("needs the files, as INVALID_ARGUMENT naming files")
  void needsTheFiles() throws Exception {
    var done = install(301, "request--install-packages-without-files.json", Map.of());

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(done)).containsEntry("param", "files");
  }

  @Test
  @DisplayName("refuses firmware among the files, naming the file by its position")
  void refusesFirmware() throws Exception {
    var done =
        install(
            302,
            "request--install-packages-two.json",
            Map.of(
                "first",
                bar("application--first.bar").toString(),
                "second",
                bar("system--operating-system.bar").toString()));

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(done)).containsEntry("param", "files[1]");
  }

  @Test
  @DisplayName("refuses a file that is not a bar, naming which")
  void refusesAFileThatIsNotABar() throws Exception {
    var done =
        install(
            303, "request--install-packages.json", Map.of("file", bar("not-a-bar.txt").toString()));

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorMessage(done)).contains("files[0]");
  }

  @Test
  @DisplayName("refuses a file that is not there, as an error of reading, naming which")
  void refusesAFileThatIsNotThere() throws Exception {
    var done =
        install(
            304,
            "request--install-packages.json",
            Map.of("file", folder.resolve("missing.bar").toString()));

    assertThat(errorCode(done)).isEqualTo("IO_ERROR");
    assertThat(errorDetails(done)).containsEntry("param", "files[0]");
  }

  @Test
  @DisplayName(
      "fails as COMMAND_FAILED when the device refuses the package, with the result sent first,"
          + " saying that nothing was installed, after the device was asked")
  void aDeviceThatRefuses() throws Exception {
    var asked = device.updateRequests.size();
    device.updateFailsAt = "send";

    var done =
        install(
            305,
            "request--install-packages.json",
            Map.of("file", bar("application--first.bar").toString()));

    assertThat(done.get("ok")).isEqualTo(false);
    assertThat(errorCode(done)).isEqualTo("COMMAND_FAILED");
    assertThat(daemon.events(305, "result")).hasSize(1);
    assertThat(array(daemon.data(305).get("installed"))).isEmpty();
    assertThat(device.updateRequests.size()).isGreaterThan(asked);
  }
}
