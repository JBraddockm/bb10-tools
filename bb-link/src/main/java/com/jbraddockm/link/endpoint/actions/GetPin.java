package com.jbraddockm.link.endpoint.actions;

import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import com.jbraddockm.link.transport.cgi.XmlConversation;
import com.jbraddockm.link.transport.session.DeviceCommand;

/**
 * Reads the device PIN. The device answers {@code <DevicePIN><PIN>…X&lt;pin&gt;</PIN></DevicePIN>};
 * the PIN is the text after the last {@code X}.
 */
public record GetPin() implements DeviceCommand<DevicePin> {

  /** The request that reads the PIN. */
  static CgiRequest.Form request() {
    return CgiRequest.Form.to(Endpoint.WIPE)
        .with(FormField.WIPE, WipeCommand.GET_PIN)
        .describedAs("Reading the device PIN");
  }

  @Override
  public Conversation<DevicePin> conversation() {
    return new XmlConversation<>() {
      @Override
      public Step<DevicePin> start() {
        return new Step.Send<>(request());
      }

      @Override
      protected Step<DevicePin> onResponse(ResponseEnvelope response) {
        return PinReader.read(response);
      }
    };
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
