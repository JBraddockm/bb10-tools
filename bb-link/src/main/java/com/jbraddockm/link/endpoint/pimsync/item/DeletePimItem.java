package com.jbraddockm.link.endpoint.pimsync.item;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import java.util.Objects;

/**
 * Removes one item — a contact, event or calendar — from a workstation's account, named by its
 * object identifier. Changes the device, so it is never repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account holds the item
 * @param service the kind of item
 * @param objectId the identifier of the item to remove
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
    return new PimSyncConversation(
        PimCommand.DELETE,
        service,
        "Removing one " + service.wireName() + " item",
        request ->
            request
                .withText(QueryParameter.WORKSTATION_ID, workstationId)
                .with(QueryParameter.SERVICE, service)
                .withText(QueryParameter.OBJECT_ID, objectId));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
