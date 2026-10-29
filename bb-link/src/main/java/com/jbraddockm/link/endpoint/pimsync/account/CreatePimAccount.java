package com.jbraddockm.link.endpoint.pimsync.account;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimAnswer;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.endpoint.pimsync.PimSyncConversation;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.QueryParameter;

/**
 * Registers a workstation as a BlackBerry Link account on the device, so that the per-service reads
 * and writes have an account to resolve to. Changes the device, so it is never repeated after a
 * dropped connection.
 *
 * <p>The device builds the stored account record itself from the two values sent here; the rest of
 * that record (the per-service flags) is the device's own and is not part of this request.
 *
 * @param workstationId the client identifier the account is keyed by (the device's {@code
 *     desktop_uid}); for BlackBerry Link this is the value in {@code devices/<PIN>/}
 * @param workstationName the human-readable name of the client
 */
public record CreatePimAccount(String workstationId, String workstationName)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if either value is missing or blank
   */
  public CreatePimAccount {
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
        PimCommand.CREATE_ACCOUNT,
        null,
        "Registering the workstation as a PIM account",
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
