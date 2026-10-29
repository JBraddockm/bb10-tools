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
 * Closes a sync round that {@link SummarizePimSync} opened, submitting the round's reconciled
 * result to the device. The round's body is the client's own: bb-link forwards it verbatim as JSON
 * and does not interpret its shape. Only contacts and events take part in a round; calendars do
 * not. Changes the device, so it is never repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account the round is for
 * @param service the kind of items the round covers — contacts or events
 * @param payload the round's reconciled result, forwarded as the JSON body unchanged
 */
public record FinishPimSync(String workstationId, PimService service, Map<String, Object> payload)
    implements DeviceCommand<PimAnswer> {

  /**
   * Validates the command and keeps an immutable copy of the payload.
   *
   * @throws InvalidArgumentException if the workstation is missing or blank, the service is not one
   *     a sync round covers, or the payload is empty
   */
  public FinishPimSync {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    if (service == PimService.CALENDARS) {
      throw new InvalidArgumentException("service", "must be contacts or events for a sync round");
    }
    Objects.requireNonNull(payload, "payload");
    if (payload.isEmpty()) {
      throw new InvalidArgumentException("payload", "must not be empty");
    }
    payload = Collections.unmodifiableMap(new LinkedHashMap<>(payload));
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    var request =
        CgiRequest.Form.to(Endpoint.PIM_SYNC)
            .with(FormField.COMMAND, PimCommand.DONE)
            .with(FormField.SERVICE, service)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.DATA, Json.write(payload))
            .describedAs("Closing a " + service.wireName() + " sync round");
    return new PimSyncConversation(PimCommand.DONE, service, request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
