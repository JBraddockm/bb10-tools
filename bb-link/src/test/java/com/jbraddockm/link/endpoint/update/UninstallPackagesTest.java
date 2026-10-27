package com.jbraddockm.link.endpoint.update;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link UninstallPackages}: which packages may be removed. */
@DisplayName("UninstallPackages: the command that removes applications")
class UninstallPackagesTest {

  private static void assertRefused(Runnable call, String parameter, String reason) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo(parameter))
        .hasMessageContaining(reason);
  }

  @ParameterizedTest(name = "\"{0}\" is a package id")
  @ValueSource(strings = {"a", "com.example.app", "Com-Example_App.1", "0123", "a.b-c_d"})
  @DisplayName("takes an identifier of letters, digits, dots, dashes and underscores")
  void takesAnIdentifier(String packageId) {
    assertThat(new UninstallPackages(List.of(packageId)).packageIds()).containsExactly(packageId);
  }

  @Test
  @DisplayName("takes an identifier of 128 characters and not one of 129")
  void takesAtMost128Characters() {
    assertThat(new UninstallPackages(List.of("a".repeat(128))).packageIds()).hasSize(1);
    assertRefused(
        () -> new UninstallPackages(List.of("a".repeat(129))),
        "packageIds[0]",
        "is not a package id");
  }

  @ParameterizedTest(name = "\"{0}\" is not a package id")
  @ValueSource(
      strings = {"", " ", "has space", "slash/x", "semi;colon", "ünï", "tab\t", "new\nline", "a&b"})
  @DisplayName("refuses an identifier with any other character, naming its position")
  void refusesOtherCharacters(String packageId) {
    assertRefused(
        () -> new UninstallPackages(List.of("fine", packageId)),
        "packageIds[1]",
        "is not a package id: letters, digits, dots, dashes and underscores, at most 128");
  }

  @Test
  @DisplayName("refuses an empty list, naming packageIds")
  void refusesNoPackages() {
    assertRefused(
        () -> new UninstallPackages(List.of()), "packageIds", "must name at least one package");
  }

  @Test
  @DisplayName("refuses the same package twice, naming the position of the second and of the first")
  void refusesTheSamePackageTwice() {
    assertRefused(
        () -> new UninstallPackages(List.of("one", "two", "one")),
        "packageIds[2]",
        "is the same package as packageIds[0]");
  }

  @Test
  @DisplayName("checks that the device has the packages unless that is switched off")
  void checksByDefault() {
    assertThat(new UninstallPackages(List.of("one")).check()).isTrue();
    assertThat(new UninstallPackages(List.of("one"), false).check()).isFalse();
  }

  @Test
  @DisplayName(
      "begins with the check of the properties, or, with no check, with the start of the removal")
  void beginsAccordingToTheCheck() {
    var checking = new UninstallPackages(List.of("one"), true).conversation().start();
    var starting = new UninstallPackages(List.of("one"), false).conversation().start();

    assertThat(sent(checking)).isEqualTo(GetDeviceProperties.request());
    assertThat(((CgiRequest.Form) sent(starting)).encodedBody()).isEqualTo("mode=app&size=0");
  }

  @Test
  @DisplayName("changes the device")
  void changesTheDevice() {
    assertThat(new UninstallPackages(List.of("one")).readOnly()).isFalse();
  }

  @Test
  @DisplayName("keeps its own copy of the list")
  void keepsItsOwnCopy() {
    var ids = new ArrayList<>(List.of("one"));
    var command = new UninstallPackages(ids);

    ids.clear();

    assertThat(command.packageIds()).containsExactly("one");
    assertThatThrownBy(() -> command.packageIds().add("two"))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
