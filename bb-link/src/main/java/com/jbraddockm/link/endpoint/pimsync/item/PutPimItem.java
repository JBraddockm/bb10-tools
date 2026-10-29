package com.jbraddockm.link.endpoint.pimsync.item;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Creates or updates one item — a contact, event or calendar — in a workstation's account. The item
 * is the client's own: bb-link forwards it to the device verbatim as JSON and does not interpret
 * its shape, which is the device's and the client's to agree on. Changes the device, so it is never
 * repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account holds the item
 * @param service the kind of item
 * @param item the item's fields, forwarded as the JSON body unchanged
 */
public record PutPimItem(String workstationId, PimService service, Map<String, Object> item)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command and keeps an immutable copy of the item.
   *
   * @throws InvalidArgumentException if the workstation is missing or blank, or the item is empty
   */
  public PutPimItem {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(item, "item");
    if (item.isEmpty()) {
      throw new InvalidArgumentException("item", "must not be empty");
    }
    item = Collections.unmodifiableMap(new LinkedHashMap<>(item));
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    var request =
        CgiRequest.Form.to(Endpoint.PIM_SYNC)
            .with(FormField.COMMAND, PimCommand.PUT)
            .with(FormField.SERVICE, service)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.JSON, Json.write(item))
            .describedAs("Writing one " + service.wireName() + " item");
    return new PimSyncConversation(PimCommand.PUT, service, request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
