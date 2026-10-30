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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Creates or updates one item — a contact, event or calendar — in a workstation's account. The
 * item's fields are the client's own and bb-link does not interpret them, but the body
 * <em>shape</em> is the device's and differs by service: the {@code contacts} and {@code events}
 * endpoints are <em>batches</em> that expect a JSON <em>array</em> of items (contacts even paginate
 * by slicing the body), so the one item is sent as a single-element array {@code [item]}; the
 * {@code calendars} endpoint takes a single calendar <em>object</em>, so it is sent as the bare
 * object. Sending the wrong shape makes the backend raise (a slice {@code TypeError} for contacts,
 * or {@code .get} on a list for calendars) and answer a generic 404. Changes the device, so it is
 * never repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account holds the item
 * @param service the kind of item
 * @param item the item's fields, sent as a one-item array for contacts/events, or bare for
 *     calendars
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
    // contacts/events are batch endpoints (a JSON array of items); a calendar is a single object.
    Object body = service == PimService.CALENDARS ? item : List.of(item);
    var request =
        CgiRequest.Form.to(Endpoint.PIM_SYNC)
            .with(FormField.COMMAND, PimCommand.PUT)
            .with(FormField.SERVICE, service)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.DATA, Json.write(body))
            .describedAs("Writing one " + service.wireName() + " item");
    return new PimSyncConversation(PimCommand.PUT, service, request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
