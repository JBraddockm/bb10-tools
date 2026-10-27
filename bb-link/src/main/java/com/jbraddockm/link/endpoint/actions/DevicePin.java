package com.jbraddockm.link.endpoint.actions;

import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.json.Json;
import java.util.Map;

/**
 * The device PIN. It identifies the device, so treat it as personal data.
 *
 * @param pin the PIN as hexadecimal text
 */
public record DevicePin(String pin) implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object("pin", pin);
  }
}
