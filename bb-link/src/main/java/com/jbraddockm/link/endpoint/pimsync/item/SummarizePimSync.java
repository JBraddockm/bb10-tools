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
 * Opens a sync round for a workstation's account and asks the device what has changed on its side.
 * The first half of a two-step round that {@link FinishPimSync} closes. Only contacts and events
 * take part in a round; calendars do not. The device may answer {@code 1016 No Changes}, which is a
 * normal result, not a rejection. Part of a sync round, so it is never repeated after a dropped
 * connection.
 *
 * @param workstationId the client identifier whose account the round is for
 * @param service the kind of items the round covers — contacts or events
 */
public record SummarizePimSync(String workstationId, PimService service)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation is missing or blank, or the service is not
   *     one a sync round covers
   */
  public SummarizePimSync {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    if (service == PimService.CALENDARS) {
      throw new InvalidArgumentException("service", "must be contacts or events for a sync round");
    }
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.SUMMARY,
        service,
        "Opening a " + service.wireName() + " sync round",
        request ->
            request
                .withText(QueryParameter.WORKSTATION_ID, workstationId)
                .with(QueryParameter.SERVICE, service));
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
