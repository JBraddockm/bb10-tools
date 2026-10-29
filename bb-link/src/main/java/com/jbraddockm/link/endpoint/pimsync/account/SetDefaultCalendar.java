package com.jbraddockm.link.endpoint.pimsync.account;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimService;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.QueryParameter;

/**
 * Sets the account's default calendar object. The service is always {@code calendars}; the device
 * accepts the {@code default} command for no other. The device resolves the workstation to its
 * account and builds the stored record itself. Changes the device, so it is never repeated after a
 * dropped connection.
 *
 * @param workstationId the client identifier whose account is changed
 * @param objectId the calendar object to make the default
 */
public record SetDefaultCalendar(String workstationId, String objectId)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if either value is missing or blank
   */
  public SetDefaultCalendar {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    if (objectId == null || objectId.isBlank()) {
      throw new InvalidArgumentException("objectId", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.DEFAULT,
        PimService.CALENDARS,
        "Setting the default calendar of a workstation",
        request ->
            request
                .withText(QueryParameter.WORKSTATION_ID, workstationId)
                .with(QueryParameter.SERVICE, PimService.CALENDARS)
                .withText(QueryParameter.OBJECT_ID, objectId));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
