package com.jbraddockm.link.properties;

import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.cgi.CgiRequest;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.Endpoint;
import com.jbraddockm.link.cgi.FormField;
import com.jbraddockm.link.cgi.ResponseElement;
import com.jbraddockm.link.cgi.ResponseEnvelope;
import com.jbraddockm.link.cgi.ResponseFailures;
import com.jbraddockm.link.cgi.Step;
import com.jbraddockm.link.cgi.XmlConversation;
import com.jbraddockm.link.session.DeviceCommand;

/** Reads the device's identity and installed packages. One request, one answer. */
public record GetDeviceProperties() implements DeviceCommand<DeviceProperties> {

  @Override
  public Conversation<DeviceProperties> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<DeviceProperties> start() {
        return new Step.Send<>(
            CgiRequest.Form.to(Endpoint.DYNAMIC_PROPERTIES)
                .echo(FormField.GET_DYNAMIC_PROPERTIES)
                .describedAs("Reading the device properties"));
      }

      @Override
      protected Step<DeviceProperties> onResponse(ResponseEnvelope response) {
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
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
