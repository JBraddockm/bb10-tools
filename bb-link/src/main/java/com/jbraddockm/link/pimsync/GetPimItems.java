package com.jbraddockm.link.pimsync;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.cgi.QueryParameter;
import com.jbraddockm.link.session.DeviceCommand;
import java.util.Objects;

/**
 * Reads the contacts, events or calendars of a workstation. Only reads.
 *
 * @param workstationId the identifier of the workstation
 * @param service the kind of items
 * @param filters how the items are narrowed down and paged
 */
public record GetPimItems(String workstationId, PimService service, PimFilters filters)
    implements DeviceCommand<PimAnswer> {

  private static final String TRUE = "true";

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation identifier is missing or blank
   */
  public GetPimItems {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    Objects.requireNonNull(service, "service");
    Objects.requireNonNull(filters, "filters");
  }

  @Override
  public Conversation<PimAnswer> conversation() {
    return new PimSyncConversation(
        PimCommand.GET,
        service,
        "Reading the " + service.wireName(),
        request -> {
          var query =
              request
                  .withText(QueryParameter.WORKSTATION_ID, workstationId)
                  .with(QueryParameter.SERVICE, service);
          if (filters.updatedTime().isPresent()) {
            query = query.withText(QueryParameter.UPDATED_TIME, filters.updatedTime().get());
          }
          if (filters.limit().isPresent()) {
            query =
                query.withText(QueryParameter.LIMIT, String.valueOf(filters.limit().getAsInt()));
          }
          if (filters.dirtyOnly()) {
            query = query.withText(QueryParameter.DIRTY_ONLY, TRUE);
          }
          if (filters.localOnly()) {
            query = query.withText(QueryParameter.LOCAL_ONLY, TRUE);
          }
          if (filters.offset().isPresent()) {
            query =
                query.withText(QueryParameter.OFFSET, String.valueOf(filters.offset().getAsInt()));
          }
          return query;
        });
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
