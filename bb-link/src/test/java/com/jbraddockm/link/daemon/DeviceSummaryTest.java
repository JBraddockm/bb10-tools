package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.testing.Steps.finished;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.actions.DevicePin;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceSummary}. */
@DisplayName("DeviceSummary: what is said of a device when it is connected")
class DeviceSummaryTest {

  private static DeviceProperties properties(String resource) {
    var text = Resources.text(GetDeviceProperties.class, resource);
    return finished(GetDeviceProperties.interpret(ResponseEnvelope.parse(text)));
  }

  private static String expected(String resource) {
    return Json.write(Json.parse(Resources.text(DeviceSummaryTest.class, resource)));
  }

  @Test
  @DisplayName(
      "is the name, the hardware, the PIN, the serial number, and the versions of the system and"
          + " the radio")
  void isWhatTheDeviceSays() {
    var summary = DeviceSummary.of(properties("properties--full.xml"), new DevicePin("2A3B4C5D"));

    assertThat(Json.write(summary.toJson())).isEqualTo(expected("device-summary--expected.json"));
  }

  @Test
  @DisplayName("has no PIN when it could not be read, and the rest as it was")
  void hasNoPinWhenItCouldNotBeRead() {
    var summary = DeviceSummary.of(properties("properties--full.xml"), null);

    assertThat(Json.write(summary.toJson()))
        .isEqualTo(expected("device-summary--without-pin-expected.json"));
  }

  @Test
  @DisplayName(
      "has nothing for what the device did not say, and no versions when it lists no system and no"
          + " radio")
  void hasNothingForWhatIsMissing() {
    var summary = DeviceSummary.of(properties("properties--minimal.xml"), null);

    assertThat(Json.write(summary.toJson()))
        .isEqualTo(expected("device-summary--minimal-expected.json"));
  }
}
