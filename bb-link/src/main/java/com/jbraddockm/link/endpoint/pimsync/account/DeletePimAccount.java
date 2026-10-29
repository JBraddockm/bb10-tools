package com.jbraddockm.link.endpoint.pimsync.account;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.QueryParameter;

/**
 * Removes the account of a workstation, de-registering it. The device resolves the workstation to
 * its account itself. Changes the device, so it is never repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account is removed
 */
public record DeletePimAccount(String workstationId) implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the identifier is missing or blank
   */
  public DeletePimAccount {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.DELETE_ACCOUNT,
        null,
        "Removing the PIM account of a workstation",
        request -> request.withText(QueryParameter.WORKSTATION_ID, workstationId));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
