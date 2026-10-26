package com.jbraddockm.link.characteristics;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.XmlConversation;
import com.jbraddockm.link.session.DeviceCommand;

/**
 * Reads the device's characteristics from {@code discovery.cgi}: one GET, one answer. Cheaper than
 * {@code getDeviceProperties} (no installed-package list), and the only PIN source over the tunnel.
 */
public record GetDeviceCharacteristics() implements DeviceCommand<DeviceCharacteristics> {

  /** The request that reads the characteristics. */
  static CgiRequest.Get request() {
    return CgiRequest.Get.to(Endpoint.DISCOVERY).describedAs("Reading the device characteristics");
  }

  /**
   * Reads the device's answer to {@link #request()}.
   *
   * @param response the answer
   * @return the characteristics as a {@link Step.Finish}, or why they could not be read as a {@link
   *     Step.Fail}
   */
  static Step<DeviceCharacteristics> interpret(ResponseEnvelope response) {
    var reported = ResponseFailures.reportedError(response);
    if (reported.isPresent()) {
      return new Step.Fail<>(reported.get());
    }
    if (response.element().orElse(null) != ResponseElement.DEVICE_CHARACTERISTICS) {
      return new Step.Fail<>(
          ResponseFailures.notExpected(response, ResponseElement.DEVICE_CHARACTERISTICS));
    }
    return DeviceCharacteristicsParser.parse(response.payload())
        .<Step<DeviceCharacteristics>>map(Step.Finish::new)
        .orElseGet(
            () ->
                new Step.Fail<>(
                    new ServiceError(
                        LinkErrorCode.UNEXPECTED_RESPONSE,
                        "The device returned empty characteristics")));
  }

  @Override
  public Conversation<DeviceCharacteristics> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<DeviceCharacteristics> start() {
        return new Step.Send<>(request());
      }

      @Override
      protected Step<DeviceCharacteristics> onResponse(ResponseEnvelope response) {
        return interpret(response);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
