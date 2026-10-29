package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pimsync.PimCommand;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import com.jbraddockm.link.transport.cgi.Endpoint;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Sends one contact photo to the device. The image is uploaded as the one part of a multipart form;
 * the contact it belongs to is named by {@code accountId} and {@code objectId}, which bb-link puts
 * into the {@code data} body the device expects ({@code {"account_id":…,"object_id":…}}). The
 * device refuses a photo over its size limit with status 1004. Changes the device, so it is never
 * repeated after a dropped connection.
 *
 * @param workstationId the client identifier whose account the photo belongs to
 * @param accountId the account the photo is stored under
 * @param objectId the contact the photo belongs to
 * @param file the image file to send
 */
public record PutPimPhoto(String workstationId, String accountId, String objectId, Path file)
    implements DeviceCommand<PimPhoto> {

  /** The multipart field name the device reads the uploaded photo from. */
  private static final String UPLOAD_FIELD = "upload_filename";

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException if the workstation, the account or the object is missing or
   *     blank
   */
  public PutPimPhoto {
    if (workstationId == null || workstationId.isBlank()) {
      throw new InvalidArgumentException("workstationId", "is required");
    }
    if (accountId == null || accountId.isBlank()) {
      throw new InvalidArgumentException("accountId", "is required");
    }
    if (objectId == null || objectId.isBlank()) {
      throw new InvalidArgumentException("objectId", "is required");
    }
    Objects.requireNonNull(file, "file");
  }

  @Override
  public Conversation<PimPhoto> conversation() {
    var data = Json.write(Json.object("account_id", accountId, "object_id", objectId));
    var request =
        CgiRequest.Multipart.of(
                Endpoint.PIM_SYNC,
                file,
                UPLOAD_FIELD,
                "image/jpeg",
                PhotoPhase.SENDING,
                "contact photo")
            .with(QueryParameter.COMMAND, PimCommand.PUT)
            .withText(QueryParameter.SERVICE, PhotoConversation.SERVICE)
            .withText(QueryParameter.WORKSTATION_ID, workstationId)
            .withText(QueryParameter.DATA, data)
            .describedAs("Sending a contact photo");
    return new PhotoConversation(request);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
