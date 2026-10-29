package com.jbraddockm.link.endpoint.pimsync.account;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.QueryParameter;

/**
 * Updates the account of a workstation that is already registered. The device resolves the
 * workstation to its account itself. Changes the device, so it is never repeated after a dropped
 * connection.
 *
 * @param workstationId the client identifier the account is keyed by
 * @param workstationName the human-readable name to set
 */
public record UpdatePimAccount(String workstationId, String workstationName)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if either value is missing or blank
   */
  public UpdatePimAccount {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    if (workstationName == null || workstationName.isBlank()) {
      throw new InvalidArgumentException("workstationName", "is required");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.UPDATE_ACCOUNT,
        null,
        "Updating the PIM account of a workstation",
        request ->
            request
                .withText(QueryParameter.WORKSTATION_ID, workstationId)
                .withText(QueryParameter.WORKSTATION_NAME, workstationName));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
