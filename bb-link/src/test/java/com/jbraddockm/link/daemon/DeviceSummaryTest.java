package com.jbraddockm.link.daemon;

import static com.jbraddockm.link.testing.Steps.finished;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.endpoint.characteristics.DeviceCharacteristics;
import com.jbraddockm.link.endpoint.properties.DeviceProperties;
import com.jbraddockm.link.endpoint.properties.GetDeviceProperties;
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
      "merges dynamicProperties.cgi (identity, serial, versions, battery) with discovery.cgi (PIN,"
          + " GUID, and the tunnel/developer/Blend/fuse flags)")
  void isWhatTheDeviceSays() {
    var characteristics =
        new DeviceCharacteristics(
            "2A3B4C5D",
            "RkFLRUdVSUQ=",
            "Passport",
            "BlackBerry 10",
            "10.3.3.2205",
            "000000000000000",
            100,
            true,
            true,
            720,
            1280,
            14_157_856_768L,
            0,
            1,
            true,
            true);
    var summary = DeviceSummary.of(properties("properties--full.xml"), characteristics);

    assertThat(Json.write(summary.toJson())).isEqualTo(expected("device-summary--expected.json"));
  }

  @Test
  @DisplayName(
      "has no PIN, GUID or flags when discovery.cgi could not be read, and the rest as it was")
  void hasNoCharacteristicsWhenDiscoveryCouldNotBeRead() {
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
