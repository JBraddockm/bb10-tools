package com.jbraddockm.link.endpoint.update;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link InstallPackages}: which packages may be installed. */
@DisplayName("InstallPackages: the command that installs applications")
class InstallPackagesTest {

  @TempDir private Path folder;

  private BarInfo bar(String name) throws Exception {
    return BarResources.inspect(name, folder);
  }

  private static void assertRefused(Runnable call, String parameter, String reason) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
        .hasMessageContaining(reason);
  }

  @Test
  @DisplayName(
      "takes applications, whatever their number, and gives the poll interval that it was made"
          + " with")
  void takesApplications() throws Exception {
    var command =
        new InstallPackages(
            List.of(bar("application--first.bar"), bar("application--second.bar")),
            Duration.ofMillis(250));

    assertThat(command.bars()).hasSize(2);
    assertThat(command.pollInterval()).isEqualTo(Duration.ofMillis(250));
  }

  @Test
  @DisplayName("of polls every second, which is what a device is asked at")
  void ofPollsEverySecond() throws Exception {
    assertThat(InstallPackages.of(List.of(bar("application--first.bar"))).pollInterval())
        .isEqualTo(Duration.ofSeconds(1));
    assertThat(InstallPackages.DEFAULT_POLL_INTERVAL).isEqualTo(Duration.ofSeconds(1));
  }

  @Test
  @DisplayName("adds up the size of the bars")
  void addsUpTheSize() throws Exception {
    var first = bar("application--first.bar");
    var larger = bar("application--larger.bar");

    assertThat(InstallPackages.of(List.of(first, larger)).totalBytes())
        .isEqualTo(Files.size(first.file()) + Files.size(larger.file()));
  }

  @Test
  @DisplayName(
      "changes the device, and its conversation begins by reading the properties, to see the free"
          + " space")
  void changesTheDevice() throws Exception {
    var command = InstallPackages.of(List.of(bar("application--first.bar")));

    assertThat(command.readOnly()).isFalse();
    assertThat(sent(command.conversation().start())).isEqualTo(GetDeviceProperties.request());
  }

  @Test
  @DisplayName("keeps its own copy of the list of bars")
  void keepsItsOwnCopy() throws Exception {
    var bars = new ArrayList<>(List.of(bar("application--first.bar")));
    var command = InstallPackages.of(bars);

    bars.clear();

    assertThat(command.bars()).hasSize(1);
    assertThatThrownBy(() -> command.bars().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  @DisplayName("refuses an empty list, naming files")
  void refusesNoBars() {
    assertRefused(() -> InstallPackages.of(List.of()), "files", "must name at least one bar");
  }

  @Test
  @DisplayName(
      "refuses an operating system image, with the words for it, naming the position of the file")
  void refusesAnOperatingSystem() throws Exception {
    var list = List.of(bar("application--first.bar"), bar("system--operating-system.bar"));

    assertRefused(
        () -> InstallPackages.of(list),
        "files[1]",
        "is an operating system image; installing firmware is not supported yet");
  }

  @Test
  @DisplayName("refuses a radio image, with the words for it")
  void refusesARadio() throws Exception {
    var list = List.of(bar("system--radio.bar"));

    assertRefused(
        () -> InstallPackages.of(list),
        "files[0]",
        "is a radio image; installing firmware is not supported yet");
  }

  @Test
  @DisplayName("refuses any other kind of package, saying which kind it is")
  void refusesOtherKinds() throws Exception {
    var list = List.of(bar("debug-token--other.bar"));

    assertRefused(
        () -> InstallPackages.of(list),
        "files[0]",
        "is a package of type 'debug-token', which cannot be installed this way");
  }

  @Test
  @DisplayName("refuses the same package twice, naming the position of the second and of the first")
  void refusesTheSamePackageTwice() throws Exception {
    var list =
        List.of(
            bar("application--first.bar"),
            bar("application--second.bar"),
            bar("application--first-again.bar"));

    assertRefused(() -> InstallPackages.of(list), "files[2]", "is the same package as files[0]");
  }

  @Test
  @DisplayName("takes two bars that have no identifier: nothing says that they are the same")
  void takesTwoBarsWithoutAnIdentifier() throws Exception {
    var other = Files.createDirectory(folder.resolve("other"));
    var list =
        List.of(
            bar("application--without-id.bar"),
            BarResources.inspect("application--without-id.bar", other));

    assertThat(InstallPackages.of(list).bars()).hasSize(2);
  }

  @Test
  @DisplayName("needs a poll interval")
  void needsAPollInterval() throws Exception {
    var list = List.of(bar("application--first.bar"));

    assertThatNullPointerException()
        .isThrownBy(() -> new InstallPackages(list, null))
        .withMessage("pollInterval");
  }
}
