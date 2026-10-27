package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import com.jbraddockm.link.endpoint.pairing.store.DeviceId;
import com.jbraddockm.link.transport.cgi.DeviceCommand;

/**
 * Pairs this computer with the device: sends the authority that the computer made to {@code
 * pairing.cgi}, with the identifier that the computer gives itself, over a session. The device
 * keeps the authority, lists the computer in Device Connections, and answers with its own authority
 * **[verified]**. Nothing else is sent: the binary of the device reads these two fields and no
 * others.
 *
 * <p>This changes the device. It is never repeated after a dropped connection.
 *
 * @param deviceId the identifier that the computer gives itself
 * @param authorityPem the computer's authority, as PEM
 */
public record PairWithDevice(String deviceId, String authorityPem)
    implements DeviceCommand<PairingReply> {

  /**
   * Checks and normalises the components.
   *
   * @throws InvalidArgumentException naming the parameter that is wrong
   */
  public PairWithDevice {
    try {
      deviceId = DeviceId.normalise(deviceId == null ? "" : deviceId);
    } catch (IllegalArgumentException invalid) {
      throw new InvalidArgumentException("deviceId", invalid.getMessage());
    }
    if (authorityPem == null || authorityPem.isBlank()) {
      throw new InvalidArgumentException("authority", "is required");
    }
  }

  @Override
  public Conversation<PairingReply> conversation() {
    var form =
        CgiRequest.Form.to(Endpoint.PAIRING)
            .withText(FormField.DEVICE_ID, deviceId)
            .withText(FormField.CERT_DATA, authorityPem);
    return new PairingExchange(form.describedAs("Pairing with the device"));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
