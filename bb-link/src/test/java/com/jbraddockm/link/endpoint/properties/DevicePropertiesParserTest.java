package com.jbraddockm.link.endpoint.properties;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.xml.XmlElement;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link DevicePropertiesParser}: a tolerant reader of the properties answer. */
@DisplayName("DevicePropertiesParser: the properties in the answer of a device")
class DevicePropertiesParserTest {

  private static Optional<DeviceProperties> parse(String resource) {
    XmlElement payload =
        ResponseEnvelope.parse(Resources.text(DevicePropertiesParserTest.class, resource))
            .payload();
    return DevicePropertiesParser.parse(payload);
  }

  @Nested
  @DisplayName("the device")
  class Device {

    @Test
    @DisplayName(
        "is read in full: the names, the identifiers, the state of the battery, the space, the"
            + " policy and the date")
    void isReadInFull() {
      var properties = parse("properties--full.xml").orElseThrow();

      assertThat(properties.deviceName()).isEqualTo("BLACKBERRY-4C5D");
      assertThat(properties.modelName()).isEqualTo("Passport");
      assertThat(properties.hardwareId()).isEqualTo("0x0a0b0c0d");
      assertThat(properties.imei()).isEqualTo("000000000000000");
      assertThat(properties.batteryLevel()).isEqualTo(87);
      assertThat(properties.batteryHealth()).isEqualTo(95);
      assertThat(properties.hasPower()).isTrue();
      assertThat(properties.freeApplicationSpace()).isEqualTo(5_368_709_120L);
      assertThat(properties.boardSerialNumber()).isEqualTo("FAKESERIAL0001");
      assertThat(properties.refurbDate()).isEqualTo("2024-01-31");
      assertThat(properties.policyRestrictions()).isEqualTo("none");
    }

    @Test
    @DisplayName("has nothing for what the device did not say, and no perimeters and no packages")
    void hasNothingForWhatIsMissing() {
      var properties = parse("properties--minimal.xml").orElseThrow();

      assertThat(properties.deviceName()).isEqualTo("BLACKBERRY-4C5D");
      assertThat(properties.modelName()).isNull();
      assertThat(properties.hardwareId()).isNull();
      assertThat(properties.imei()).isNull();
      assertThat(properties.batteryLevel()).isNull();
      assertThat(properties.batteryHealth()).isNull();
      assertThat(properties.hasPower()).isNull();
      assertThat(properties.freeApplicationSpace()).isNull();
      assertThat(properties.boardSerialNumber()).isNull();
      assertThat(properties.policyRestrictions()).isNull();
      assertThat(properties.perimeters()).isEmpty();
      assertThat(properties.packages()).isEmpty();
      assertThat(properties.removedPackages()).isEmpty();
    }

    @Test
    @DisplayName("has no properties when the answer has nothing inside it")
    void hasNoPropertiesWhenTheAnswerIsEmpty() {
      assertThat(parse("properties--empty.xml")).isEmpty();
    }

    @Test
    @DisplayName(
        "takes a number that is not a number as nothing, and a flag that is not true as false")
    void numbersThatAreNotNumbers() {
      var properties = parse("properties--numbers-that-are-not-numbers.xml").orElseThrow();

      assertThat(properties.batteryLevel()).isNull();
      assertThat(properties.batteryHealth()).isNull();
      assertThat(properties.freeApplicationSpace()).isNull();
      assertThat(properties.hasPower()).isFalse();
    }
  }

  @Nested
  @DisplayName("the perimeters")
  class Perimeters {

    @Test
    @DisplayName("are read with their kind and their BlackBerry ID, in the order of the answer")
    void areRead() {
      assertThat(parse("properties--full.xml").orElseThrow().perimeters())
          .containsExactly(
              new Perimeter(PerimeterKind.PERSONAL, "bbid-personal"),
              new Perimeter(PerimeterKind.ENTERPRISE, "bbid-enterprise"));
    }

    @Test
    @DisplayName(
        "take their kind from the name of the element when it has no type, and have no ID when none"
            + " was given")
    void takeTheKindFromTheName() {
      assertThat(parse("properties--perimeters-by-name.xml").orElseThrow().perimeters())
          .containsExactly(
              new Perimeter(PerimeterKind.PERSONAL, "bbid-a"),
              new Perimeter(PerimeterKind.ENTERPRISE, null),
              new Perimeter(PerimeterKind.UNKNOWN, "bbid-c"));
    }
  }

  @Nested
  @DisplayName("the types of perimeter of the platform")
  class PlatformPerimeters {

    @Test
    @DisplayName(
        "are read by their names: Default and Personal are the personal perimeter, Enterprise the"
            + " enterprise one")
    void areReadByTheirNames() {
      assertThat(parse("properties--perimeters-platform-names.xml").orElseThrow().perimeters())
          .containsExactly(
              new Perimeter(PerimeterKind.PERSONAL, "bbid-default"),
              new Perimeter(PerimeterKind.ENTERPRISE, "bbid-enterprise"),
              new Perimeter(PerimeterKind.PERSONAL, "bbid-personal"));
    }
  }

  @Nested
  @DisplayName("the packages")
  class Packages {

    @Test
    @DisplayName(
        "are read in the order of the answer, each with its type, its identifiers, its version and"
            + " its fingerprint")
    void areRead() {
      var packages = parse("properties--full.xml").orElseThrow().packages();

      assertThat(packages).hasSize(5);
      assertThat(packages.get(0))
          .isEqualTo(
              new InstalledPackage(
                  "sys.os.test",
                  PackageType.OPERATING_SYSTEM,
                  "os1",
                  "osv1",
                  "10.3.3.2205",
                  "fingerprint-os"));
      assertThat(packages.get(1))
          .isEqualTo(
              new InstalledPackage(
                  "sys.radio.test",
                  PackageType.RADIO,
                  "radio1",
                  "radiov1",
                  "10.3.3.2206",
                  "fingerprint-radio"));
      assertThat(packages.get(2))
          .isEqualTo(
              new InstalledPackage(
                  "com.example.app",
                  PackageType.APPLICATION,
                  "app1",
                  "appv1",
                  "1.0.0",
                  "fingerprint-app"));
    }

    @Test
    @DisplayName(
        "have nothing for what was not said, and the type of a package that has an empty type is"
            + " unclassified, and an odd one unknown")
    void haveNothingForWhatIsMissing() {
      var packages = parse("properties--full.xml").orElseThrow().packages();

      assertThat(packages.get(3))
          .isEqualTo(
              new InstalledPackage(
                  "com.example.plain", PackageType.UNCLASSIFIED, "plain1", null, null, null));
      assertThat(packages.get(4))
          .isEqualTo(
              new InstalledPackage(
                  "com.example.odd", PackageType.UNKNOWN, "odd1", null, null, null));
    }

    @Test
    @DisplayName("that were removed are a list of their own, and are not among the installed ones")
    void removedPackagesAreApart() {
      var properties = parse("properties--full.xml").orElseThrow();

      assertThat(properties.removedPackages())
          .containsExactly(
              new InstalledPackage(
                  "com.example.gone", PackageType.APPLICATION, "gone1", null, "0.9", null));
      assertThat(properties.packages())
          .extracting(InstalledPackage::name)
          .doesNotContain("com.example.gone");
    }
  }
}
