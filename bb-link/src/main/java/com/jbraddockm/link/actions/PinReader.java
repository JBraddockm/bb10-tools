package com.jbraddockm.link.actions;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;

/**
 * Reads the answer to the request for the PIN: {@code
 * <DevicePIN><PIN>…X&lt;pin&gt;</PIN></DevicePIN>}; the PIN is the text after the last {@code X},
 * in either case.
 */
final class PinReader {

  private PinReader() {}

  static Step<DevicePin> read(ResponseEnvelope response) {
    var reported = ResponseFailures.reportedError(response);
    if (reported.isPresent()) {
      return new Step.Fail<>(reported.get());
    }
    if (response.element().orElse(null) != ResponseElement.DEVICE_PIN) {
      return new Step.Fail<>(ResponseFailures.notExpected(response, ResponseElement.DEVICE_PIN));
    }
    var text = response.payload().childText("PIN").orElse("");
    var marker = Math.max(text.lastIndexOf('X'), text.lastIndexOf('x'));
    var pin = text.substring(marker + 1).strip();
    if (pin.isEmpty()) {
      return new Step.Fail<>(
          new ServiceError(
              LinkErrorCode.UNEXPECTED_RESPONSE,
              "The device answered without a PIN",
              Json.object("element", response.name())));
    }
    return new Step.Finish<>(new DevicePin(pin));
  }
}
