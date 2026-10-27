package com.jbraddockm.link.characteristics;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiResponse;
import com.jbraddockm.link.cgi.Endpoint;
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
    return finished(answer(resource));
  }

  private static Step<DeviceCharacteristics> answer(String resource) {
    var body = Resources.text(GetDeviceCharacteristicsTest.class, resource);
    var response = new CgiResponse(200, "text/xml", body, null);
    return new GetDeviceCharacteristics()
        .conversation()
        .onAnswer(response, ResponseEnvelope.parse(body));
  }

  @Nested
  @DisplayName("the request it sends")
  class TheRequest {

    @Test
    @DisplayName("asks discovery.cgi, and says that it is reading the characteristics")
    void asksDiscoveryCgi() {
      var request = GetDeviceCharacteristics.request();

      assertThat(request.endpoint()).isEqualTo(Endpoint.DISCOVERY);
      assertThat(request.describe()).isEqualTo("Reading the device characteristics");
    }

    @Test
    @DisplayName("begins by sending that request, and nothing else")
    void beginsWithTheRequest() {
      assertThat(sent(new GetDeviceCharacteristics().conversation().start()))
          .isEqualTo(GetDeviceCharacteristics.request());
    }

    @Test
    @DisplayName("only reads: it changes nothing on the device")
    void onlyReads() {
      assertThat(new GetDeviceCharacteristics().readOnly()).isTrue();
    }
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

  @Nested
  @DisplayName("a response whose numeric fields are not numbers")
  class MalformedValues {

    private final DeviceCharacteristics characteristics = parse("discovery--synthetic-malformed.xml");

    @Test
    @DisplayName("drops each unparseable number rather than failing the whole read")
    void dropsUnparseableNumbers() {
      assertThat(characteristics.pin()).isNull();
      assertThat(characteristics.batteryLevel()).isNull();
      assertThat(characteristics.screenWidth()).isNull();
      assertThat(characteristics.flashSize()).isNull();
      assertThat(characteristics.sdCard()).isNull();
      assertThat(characteristics.blendPolicy()).isNull();
    }

    @Test
    @DisplayName("still reads the fields that are well formed")
    void keepsTheWellFormedFields() {
      assertThat(characteristics.modelName()).isEqualTo("STR100-1");
      assertThat(characteristics.osType()).isEqualTo("BlackBerry 10");
    }
  }

  @Nested
  @DisplayName("an answer it cannot turn into characteristics")
  class FailedAnswers {

    @Test
    @DisplayName("surfaces an error the device reported in place of the characteristics")
    void reportsTheDeviceError() {
      var error = failed(answer("discovery--synthetic-error.xml"));

      assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(error.message()).contains("discovery is not available");
    }

    @Test
    @DisplayName("fails when the device answers with a different element")
    void failsForAnUnexpectedElement() {
      var error = failed(answer("discovery--synthetic-other-element.xml"));

      assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(error.message()).contains("Expected DeviceCharacteristics").contains("DevicePIN");
    }

    @Test
    @DisplayName("fails when the characteristics element is present but empty")
    void failsForEmptyCharacteristics() {
      var error = failed(answer("discovery--synthetic-empty.xml"));

      assertThat(error.code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(error.message()).contains("empty characteristics");
    }
  }
}
