package com.jbraddockm.link.endpoint.properties;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.ResponseElement;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.ResponseFailures;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;

/** Reads the device's identity and installed packages. One request, one answer. */
public record GetDeviceProperties() implements DeviceCommand<DeviceProperties> {

  /**
   * Returns the request that reads the properties, for conversations that need them as one of their
   * steps.
   *
   * @return the request
   */
  public static CgiRequest.Form request() {
    return CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES)
        .echo(FormField.GET_DYNAMIC_PROPERTIES)
        .describedAs("Reading the device properties");
  }

  /**
   * Reads the device's answer to {@link #request()}.
   *
   * @param response the answer
   * @return the properties as a {@link Step.Finish}, or why they could not be read as a {@link
   *     Step.Fail}
   */
  public static Step<DeviceProperties> interpret(ResponseEnvelope response) {
    var reported = ResponseFailures.reportedError(response);
    if (reported.isPresent()) {
      return new Step.Fail<>(reported.get());
    }
    if (response.element().orElse(null) != ResponseElement.DYNAMIC_PROPERTIES) {
      return new Step.Fail<>(
          ResponseFailures.notExpected(response, ResponseElement.DYNAMIC_PROPERTIES));
    }
    return DevicePropertiesParser.parse(response.payload())
        .<Step<DeviceProperties>>map(Step.Finish::new)
        .orElseGet(
            () ->
                new Step.Fail<>(
                    new ServiceError(
                        LinkErrorCode.UNEXPECTED_RESPONSE,
                        "The device returned empty properties")));
  }

  @Override
  public Conversation<DeviceProperties> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<DeviceProperties> start() {
        return new Step.Send<>(request());
      }

      @Override
      protected Step<DeviceProperties> onResponse(ResponseEnvelope response) {
        return interpret(response);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
