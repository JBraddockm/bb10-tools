package com.jbraddockm.link.endpoint.pimsync.photo;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.QueryParameter;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for PutPimPhoto: the multipart request that sends a contact photo. */
@DisplayName("PutPimPhoto: the request that sends one photo")
class PutPimPhotoTest {

  private static CgiRequest.Multipart requestOf(PutPimPhoto command) {
    return (CgiRequest.Multipart) sent(command.conversation().start());
  }

  @Test
  @DisplayName("posts the file as upload_filename, with the descriptor's inner fields in data")
  void postsTheFile() {
    var file = Path.of("photo.jpg");
    var request = requestOf(new PutPimPhoto("WS1", "ACC1", "OBJ1", file));

    assertThat(request.query())
        .containsEntry(QueryParameter.COMMAND, "put")
        .containsEntry(QueryParameter.SERVICE, "photos")
        .containsEntry(QueryParameter.WORKSTATION_ID, "WS1")
        // the CGI wraps data as [{<data>,"filepath":…}], so data is the inner fields, braces-free,
        // and the backend finds the contact by its id.
        .containsEntry(QueryParameter.DATA, "\"id\":\"OBJ1\",\"account_id\":\"ACC1\"");
    assertThat(request.query().get(QueryParameter.DATA)).doesNotStartWith("{").doesNotEndWith("}");
    assertThat(request.file()).isEqualTo(file);
    assertThat(request.fileField()).isEqualTo("upload_filename");
    assertThat(request.fileContentType()).isEqualTo("image/jpeg");
    assertThat(request.describe()).isEqualTo("Sending a contact photo");
  }

  @Test
  @DisplayName("changes the device, so it is not read-only")
  void changesTheDevice() {
    assertThat(new PutPimPhoto("WS1", "ACC1", "OBJ1", Path.of("photo.jpg")).readOnly()).isFalse();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(() -> new PutPimPhoto(workstation, "ACC1", "OBJ1", Path.of("photo.jpg")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not an account")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs an account that is not blank, naming accountId")
  void needsAnAccount(String account) {
    assertThatThrownBy(() -> new PutPimPhoto("WS1", account, "OBJ1", Path.of("photo.jpg")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("accountId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not an object")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs an object that is not blank, naming objectId")
  void needsAnObject(String object) {
    assertThatThrownBy(() -> new PutPimPhoto("WS1", "ACC1", object, Path.of("photo.jpg")))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("objectId"));
  }

  @Test
  @DisplayName("needs a file")
  void needsAFile() {
    assertThatNullPointerException()
        .isThrownBy(() -> new PutPimPhoto("WS1", "ACC1", "OBJ1", null))
        .withMessage("file");
  }
}
