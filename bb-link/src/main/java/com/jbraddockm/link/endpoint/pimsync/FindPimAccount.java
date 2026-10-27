package com.jbraddockm.link.endpoint.pimsync;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import com.jbraddockm.link.transport.session.DeviceCommand;

/**
 * Looks up the account that belongs to a workstation. Only reads.
 *
 * @param workstationId the identifier of the workstation
 */
public record FindPimAccount(String workstationId) implements DeviceCommand<PimAnswer> {

  /**
   * Validates the identifier.
   *
   * @throws InvalidArgumentException if it is missing or blank
   */
  public FindPimAccount {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.FIND_ACCOUNT_ID,
        null,
        "Looking up the PIM account of a workstation",
        request -> request.withText(QueryParameter.WORKSTATION_ID, workstationId));
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
