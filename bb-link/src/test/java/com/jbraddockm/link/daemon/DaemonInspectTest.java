package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.daemon.DaemonRun.errorCode;
import static com.jbraddockm.link.daemon.DaemonRun.errorDetails;
import static com.jbraddockm.link.daemon.DaemonRun.errorMessage;
import static com.jbraddockm.link.testing.JsonObjects.object;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.session.ExchangeObserver;
import com.jbraddockm.link.session.TransportScheme;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.update.InstallPackages;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Protocol-level tests for {@code inspectPackage}. */
@DisplayName("The daemon: inspectPackage")
class DaemonInspectTest {

  @TempDir private Path folder;
  private DaemonRun daemon;

  @BeforeEach
  void startTheDaemon() throws Exception {
    daemon = new DaemonRun(new LinkService(TransportScheme.HTTP, _ -> {}, ExchangeObserver.NONE));
    daemon.hello();
  }

  @AfterEach
  void stopTheDaemon() throws Exception {
    daemon.close();
  }

  private Path copy(String name) {
    return Resources.copyTo(InstallPackages.class, name, folder);
  }

  private Map<String, Object> inspect(long id, String resource, Path file) throws Exception {
    daemon.send(id, resource, Map.of("file", file.toString()));
    return daemon.done(id);
  }

  @Test
  @DisplayName(
      "gives the kind, the identifier, the version, the architecture, the file and the size of an"
          + " application, and no validation unless it is asked for")
  void givesWhatTheBarSays() throws Exception {
    var file = copy("application--first.bar");

    assertThat(inspect(200, "request--inspect-package.json", file).get("ok")).isEqualTo(true);

    var inspected = daemon.data(200);
    assertThat(inspected)
        .containsEntry("kind", "application")
        .containsEntry("packageId", "idFirst")
        .containsEntry("packageVersion", "1.0.0.1")
        .containsEntry("architecture", "armle-v7");
    assertThat(inspected)
        .containsEntry("file", file.toString())
        .containsEntry("sizeBytes", Files.size(file))
        .doesNotContainKey("validation");
  }

  @Test
  @DisplayName(
      "gives the validation too when it is asked for: which rules, a verdict and the findings")
  void givesTheValidation() throws Exception {
    var file = copy("application--first.bar");

    assertThat(inspect(201, "request--inspect-package-validate.json", file).get("ok"))
        .isEqualTo(true);

    var validation = object(daemon.data(201).get("validation"));
    assertThat(validation).containsEntry("rules", "unsigned");
    assertThat(validation.get("valid")).isInstanceOf(Boolean.class);
    assertThat(validation.get("messages")).isInstanceOf(List.class);
  }

  @Test
  @DisplayName("reads a radio image, and says that it is one")
  void readsARadioImage() throws Exception {
    assertThat(inspect(207, "request--inspect-package.json", copy("system--radio.bar")).get("ok"))
        .isEqualTo(true);

    assertThat(daemon.data(207)).containsEntry("kind", "radio");
  }

  @Test
  @DisplayName("needs the file, as INVALID_ARGUMENT naming file")
  void needsTheFile() throws Exception {
    daemon.send(202, "request--inspect-package-without-file.json");

    var done = daemon.done(202);

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorDetails(done)).containsEntry("param", "file");
  }

  @Test
  @DisplayName("says that a file is not there, as an error of reading")
  void aFileThatIsNotThere() throws Exception {
    var done = inspect(203, "request--inspect-package.json", folder.resolve("missing.bar"));

    assertThat(errorCode(done)).isEqualTo("IO_ERROR");
    assertThat(errorMessage(done)).isEqualTo("The file does not exist");
  }

  @Test
  @DisplayName("says that a file that is not an archive is not a usable bar")
  void aFileThatIsNotABar() throws Exception {
    var done = inspect(204, "request--inspect-package.json", copy("not-a-bar.txt"));

    assertThat(errorCode(done)).isEqualTo("INVALID_ARGUMENT");
    assertThat(errorMessage(done)).contains("not a usable bar");
  }

  @Test
  @DisplayName("refuses a bar of a package type that is not known, and a folder")
  void anUnknownTypeAndAFolder() throws Exception {
    var unknown = Resources.copyTo(DaemonInspectTest.class, "bar--unknown-type.bar", folder);

    assertThat(errorCode(inspect(205, "request--inspect-package.json", unknown)))
        .isEqualTo("INVALID_ARGUMENT");
    assertThat(errorCode(inspect(206, "request--inspect-package.json", folder)))
        .isEqualTo("INVALID_ARGUMENT");
  }
}
