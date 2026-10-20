package com.jbraddockm.link.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.cgi.ResponseBody;
import com.jbraddockm.link.testing.Resources;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the results of installing, removing and inspecting packages: their JSON form and
 * defensive copies.
 */
@DisplayName(
    "InstallResult, UninstallResult, InstalledPackage and PackageInspection: what an update gives")
class UpdateResultsTest {

  @TempDir private Path folder;

  private static String written(Object value) {
    return Json.write(value);
  }

  @Test
  @DisplayName(
      "an installed package is what its bar says: the file, the identifier, the name, the version"
          + " and the size")
  void anInstalledPackage() throws Exception {
    var bar = BarResources.inspect("application--first.bar", folder);

    var installed = InstalledPackage.of(bar);

    assertThat(installed.file()).isEqualTo(bar.file().toString());
    assertThat(installed.packageId()).isEqualTo("idFirst");
    assertThat(installed.packageName()).isEqualTo("Test App");
    assertThat(installed.packageVersion()).isEqualTo("1.0.0.1");
    assertThat(installed.sizeBytes()).isEqualTo(Files.size(bar.file()));
  }

  @Test
  @DisplayName(
      "an installation gives its packages and the answer of the device as JSON, as the expected"
          + " file says")
  void anInstallationAsJson() throws Exception {
    var bar = BarResources.inspect("application--first.bar", folder);
    var expected =
        Resources.text(
            UpdateResultsTest.class,
            "install-result--expected.json",
            Map.of("file", bar.file().toString(), "size", String.valueOf(Files.size(bar.file()))));
    var result =
        new InstallResult(
            List.of(InstalledPackage.of(bar)),
            ResponseBody.of(
                Resources.text(
                    UpdateResultsTest.class,
                    "update--answer-without-status.xml",
                    Map.of("element", "UpdateEnd"))));

    assertThat(written(result.toJson())).isEqualTo(written(Json.parse(expected)));
  }

  @Test
  @DisplayName(
      "a removal gives the identifiers that were removed and the answer of the device as JSON")
  void aRemovalAsJson() {
    var result = new UninstallResult(List.of("idFirst", "idOther"), ResponseBody.of("plain text"));

    assertThat(result.toJson()).containsOnlyKeys("removed", "bodyFormat", "body");
    assertThat(result.toJson())
        .containsEntry("removed", List.of("idFirst", "idOther"))
        .containsEntry("bodyFormat", "text")
        .containsEntry("body", "plain text");
  }

  @Test
  @DisplayName("keeps its own copy of the lists that it was given, and needs the answer")
  void keepsItsOwnCopy() throws Exception {
    var installed =
        new ArrayList<>(
            List.of(InstalledPackage.of(BarResources.inspect("application--first.bar", folder))));
    var removed = new ArrayList<>(List.of("idFirst"));
    var install = new InstallResult(installed, ResponseBody.of("x"));
    var uninstall = new UninstallResult(removed, ResponseBody.of("x"));

    installed.clear();
    removed.clear();

    assertThat(install.installed()).hasSize(1);
    assertThat(uninstall.removed()).containsExactly("idFirst");
    assertThatThrownBy(() -> install.installed().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> new InstallResult(List.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("answer");
    assertThatThrownBy(() -> new UninstallResult(List.of(), null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("answer");
  }

  @Test
  @DisplayName(
      "an inspection gives what the bar says, and its validation only when it was asked for")
  void anInspectionAsJson() throws Exception {
    var file = BarResources.copy("application--first.bar", folder);
    var info = BarInspector.inspect(file);

    var plain = new PackageInspection(info, null).toJson();
    var validated = new PackageInspection(info, BarInspector.validate(file)).toJson();

    assertThat(plain).isEqualTo(info.toJson()).doesNotContainKey("validation");
    assertThat(validated).containsEntry("validation", BarInspector.validate(file).toJson());
    assertThat(validated.keySet()).containsAll(info.toJson().keySet());
  }

  @Test
  @DisplayName("an inspection needs the information of the bar")
  void anInspectionNeedsTheInfo() {
    assertThatThrownBy(() -> new PackageInspection(null, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("info");
  }
}
