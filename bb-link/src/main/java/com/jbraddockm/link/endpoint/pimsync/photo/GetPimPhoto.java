package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.FormField;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Fetches one contact photo from the device and streams it to a file. The {@code path} names the
 * photo on the device, as a contact's item gives it; the bytes are written to {@code destination}
 * and never pass through memory as text. An {@code updatedTime} makes the fetch conditional: if the
 * photo has not changed since then the device answers {@code 1016 No Changes} and no file is
 * written. Only reads, so it is retried once after a dropped connection.
 *
 * @param workstationId the client identifier whose account the photo belongs to
 * @param path the device path of the photo, from a contact item
 * @param destination the file the image is written to
 * @param updatedTime a time the fetch is conditional on, or empty for an unconditional fetch
 */
public record GetPimPhoto(
    String workstationId, String path, Path destination, Optional<String> updatedTime)
    implements DeviceCommand<PimPhoto> {

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation or the path is missing or blank
   */
  public GetPimPhoto {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    if (path == null || path.isBlank()) {
      throw new InvalidArgumentException("path", "is required");
    }
    Objects.requireNonNull(destination, "destination");
    Objects.requireNonNull(updatedTime, "updatedTime");
  }

  @Override
  public Conversation<PimPhoto> conversation() {
    var request =
        CgiRequest.Download.to(
                Endpoint.PIM_SYNC, destination, 0, PhotoPhase.FETCHING, "contact photo")
            .with(FormField.COMMAND, PimCommand.GET)
            .withText(FormField.SERVICE, PhotoConversation.SERVICE)
            .withText(FormField.WORKSTATION_ID, workstationId)
            .withText(FormField.PATH, path);
    if (updatedTime.isPresent()) {
      request = request.withText(FormField.UPDATED_TIME, updatedTime.get());
    }
    return new PhotoConversation(request.describedAs("Fetching a contact photo"));
  }

  @Override
  public boolean readOnly() {
    return true;
  }
}
