package com.jbraddockm.link.endpoint.properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.testing.Resources;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceProperties}. */
@DisplayName("DeviceProperties: the properties of a device as a result")
class DevicePropertiesTest {

  private static DeviceProperties properties(String resource) {
    return DevicePropertiesParser.parse(
            ResponseEnvelope.parse(Resources.text(DevicePropertiesTest.class, resource)).payload())
        .orElseThrow();
  }

  @Test
  @DisplayName(
      "gives as JSON what the expected file says, field by field, with null where a package has"
          + " nothing")
  void givesTheExpectedJson() {
    var expected =
        Json.write(
            Json.parse(
                Resources.text(DevicePropertiesTest.class, "properties--full--expected.json")));

    assertThat(Json.write(properties("properties--full.xml").toJson())).isEqualTo(expected);
  }

  @Test
  @DisplayName("has the system and the radio that are the first package of their type")
  void hasTheSystemAndTheRadio() {
    var properties = properties("properties--full.xml");

    assertThat(properties.operatingSystem())
        .hasValueSatisfying(system -> assertThat(system.name()).isEqualTo("sys.os.test"));
    assertThat(properties.radio())
        .hasValueSatisfying(radio -> assertThat(radio.name()).isEqualTo("sys.radio.test"));
  }

  @Test
  @DisplayName(
      "takes the first system when there are two, and has no radio when there is none, which the"
          + " JSON says as null")
  void takesTheFirstOfTwo() {
    var properties = properties("properties--two-systems.xml");

    assertThat(properties.operatingSystem())
        .hasValueSatisfying(system -> assertThat(system.name()).isEqualTo("first.os"));
    assertThat(properties.radio()).isEmpty();
    var device = (Map<?, ?>) properties.toJson().get("device");
    assertThat(device.get("os")).isEqualTo(Json.object("name", "first.os", "version", "1"));
    assertThat(device.get("radio")).isNull();
  }

  @Test
  @DisplayName(
      "keeps its own copy of the lists that it was given, which cannot be changed afterwards by"
          + " anyone")
  void keepsItsOwnCopy() {
    var given =
        new ArrayList<>(List.of(new Perimeter(PerimeterKind.ENTERPRISE, "bbid-enterprise")));
    var properties =
        new DeviceProperties(
            "name", null, null, null, null, null, null, null, null, null, null, given, List.of(),
            List.of());

    given.clear();

    assertThat(properties.perimeters()).hasSize(1);
    assertThatThrownBy(
            () -> properties.perimeters().add(new Perimeter(PerimeterKind.PERSONAL, "x")))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> properties.packages().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> properties.removedPackages().add(null))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
