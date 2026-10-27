package com.jbraddockm.link.endpoint.characteristics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link DeviceCharacteristics}: how it presents itself as the command's JSON result. */
@DisplayName("DeviceCharacteristics: the JSON a client receives")
class DeviceCharacteristicsTest {

  private static final DeviceCharacteristics FULL =
      new DeviceCharacteristics(
          "2A3B4C5D",
          "Z3VpZA==",
          "STR100-1",
          "BlackBerry 10",
          "10.3.3.3216",
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

  @Test
  @DisplayName("renders every field under its own key")
  void rendersEveryField() {
    assertThat(FULL.toJson())
        .containsOnlyKeys(
            "pin",
            "guid",
            "modelName",
            "osType",
            "platformVersion",
            "imei",
            "batteryLevel",
            "developerMode",
            "tunnelUp",
            "screenWidth",
            "screenHeight",
            "flashSize",
            "sdCard",
            "blendPolicy",
            "fuseEnabled",
            "fuseInstalled")
        .containsEntry("pin", "2A3B4C5D")
        .containsEntry("modelName", "STR100-1")
        .containsEntry("platformVersion", "10.3.3.3216")
        .containsEntry("batteryLevel", 100)
        .containsEntry("developerMode", true)
        .containsEntry("flashSize", 14_157_856_768L)
        .containsEntry("blendPolicy", 1);
  }
}
