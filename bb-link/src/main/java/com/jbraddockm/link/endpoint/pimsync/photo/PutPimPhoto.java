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
 * the contact it belongs to is named by its {@code objectId} (its device contact id). The device's
 * photo endpoint is a <em>batch</em>: the CGI wraps the {@code data} field as {@code
 * [{<data>,"filepath":"…"}]} (adding the uploaded file's path), and the backend iterates that array
 * and finds each contact by its {@code id}. So {@code data} is the descriptor's inner fields with
 * no enclosing braces ({@code "id":…,"account_id":…}); bb-link does not send a bare {@code
 * {"account_id":…,"object_id":…}} object (that would wrap to malformed JSON). The device refuses a
 * photo over its size limit with status 1004. Changes the device, so it is never repeated after a
 * dropped connection.
 *
 * @param workstationId the client identifier whose account the photo belongs to
 * @param accountId the account the photo is stored under
 * @param objectId the device contact id the photo belongs to
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
    // The backend finds the contact by "id"; account_id is harmless context. The CGI wraps data as
    // [{<data>,"filepath":"…"}], so data is the descriptor's inner fields with no enclosing
    // braces — strip them from the serialized object.
    var object = Json.write(Json.object("id", objectId, "account_id", accountId));
    var data = object.substring(1, object.length() - 1);
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
