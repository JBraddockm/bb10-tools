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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Removes one item — a contact, event or calendar — from a workstation's account, named by its
 * object identifier. The device's delete endpoint is a <em>batch</em>: it reads the ids from the
 * body as {@code {"type":"id","ids":[…]}} (the {@code type} selects whether an id is the device's
 * own object id or a sync id). So the one object id is sent in the body as a one-element {@code
 * ids} array; it is not an {@code object_id} query parameter. Changes the device, so it is never
 * repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account holds the item
 * @param service the kind of item
 * @param objectId the device object identifier of the item to remove
 */
public record DeletePimItem(String workstationId, PimService service, String objectId)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation or the object identifier is missing or
   *     blank
   */
  public DeletePimItem {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    if (objectId == null || objectId.isBlank()) {
      throw new InvalidArgumentException("objectId", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    var request =
        CgiRequest.Form.to(Endpoint.PIM_SYNC)
            .with(FormField.COMMAND, PimCommand.DELETE)
            .with(FormField.SERVICE, service)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.DATA, Json.write(Map.of("type", "id", "ids", List.of(objectId))))
            .describedAs("Removing one " + service.wireName() + " item");
    return new PimSyncConversation(PimCommand.DELETE, service, request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
