package com.jbraddockm.link.actions;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.session.DeviceCommand;

/**
 * Reads the device PIN. The device answers {@code <DevicePIN><PIN>…X&lt;pin&gt;</PIN></DevicePIN>};
 * the PIN is the text after the last {@code X}.
 */
public record GetPin() implements DeviceCommand<DevicePin> {

  @Override
  public Conversation<DevicePin> conversation() {
    return new Conversation<>() {
      @Override
      public Step<DevicePin> start() {
        return new Step.Send<>(
            CgiRequest.Form.to(Endpoint.WIPE)
                .with(FormField.WIPE, WipeCommand.GET_PIN)
                .describedAs("Reading the device PIN"));
      }

      @Override
      public Step<DevicePin> onResponse(ResponseEnvelope response) {
        var reported = ResponseFailures.reportedError(response);
        if (reported.isPresent()) {
          return new Step.Fail<>(reported.get());
        }
        if (response.element().orElse(null) != ResponseElement.DEVICE_PIN) {
          return new Step.Fail<>(
              ResponseFailures.notExpected(response, ResponseElement.DEVICE_PIN));
        }
        var text = response.payload().childText("PIN").orElse("");
        var pin = text.substring(text.lastIndexOf('X') + 1).strip();
        if (pin.isEmpty()) {
          return new Step.Fail<>(
              new ServiceError(
                  LinkErrorCode.UNEXPECTED_RESPONSE,
                  "The device answered without a PIN",
                  Json.object("element", response.name())));
        }
        return new Step.Finish<>(new DevicePin(pin));
      }
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
