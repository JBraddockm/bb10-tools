package com.jbraddockm.link.characteristics;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.testing.Resources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link GetDeviceCharacteristics}: reading {@code discovery.cgi} into a typed record.
 */
@DisplayName("GetDeviceCharacteristics: reading discovery.cgi")
class GetDeviceCharacteristicsTest {

  private static DeviceCharacteristics parse(String resource) {
    var envelope =
        ResponseEnvelope.parse(Resources.text(GetDeviceCharacteristicsTest.class, resource));
    var step = GetDeviceCharacteristics.interpret(envelope);
    assertThat(step).isInstanceOf(Step.Finish.class);
    return ((Step.Finish<DeviceCharacteristics>) step).result();
  }

  @Nested
  @DisplayName("a real device response")
  class RealResponse {

    private final DeviceCharacteristics characteristics = parse("discovery--real.xml");

    @Test
    @DisplayName("formats the decimal BbPin as the 8-digit uppercase hex PIN")
    void formatsThePin() {
      assertThat(characteristics.pin()).isEqualTo("2C1E7583");
    }

    @Test
    @DisplayName("reads the identity fields")
    void readsTheIdentity() {
      assertThat(characteristics.guid()).isEqualTo("EDunxOMuoNpdOlvSIBPJD1dfcMs=");
      assertThat(characteristics.modelName()).isEqualTo("STR100-1");
      assertThat(characteristics.osType()).isEqualTo("BlackBerry 10");
      assertThat(characteristics.platformVersion()).isEqualTo("10.3.3.3216");
      assertThat(characteristics.imei()).isEqualTo("354359061740215");
    }

    @Test
    @DisplayName("reads the numeric and boolean status fields")
    void readsTheStatus() {
      assertThat(characteristics.batteryLevel()).isEqualTo(100);
      assertThat(characteristics.developerMode()).isTrue();
      assertThat(characteristics.tunnelUp()).isTrue();
      assertThat(characteristics.screenWidth()).isEqualTo(720);
      assertThat(characteristics.screenHeight()).isEqualTo(1280);
      assertThat(characteristics.flashSize()).isEqualTo(14_157_856_768L);
    }

    @Test
    @DisplayName("reads the capability flags a client acts on (SD card, Blend, fuse)")
    void readsTheCapabilityFlags() {
      assertThat(characteristics.sdCard()).isEqualTo(0);
      assertThat(characteristics.blendPolicy()).isEqualTo(1);
      assertThat(characteristics.fuseEnabled()).isTrue();
      assertThat(characteristics.fuseInstalled()).isTrue();
    }
  }
}
