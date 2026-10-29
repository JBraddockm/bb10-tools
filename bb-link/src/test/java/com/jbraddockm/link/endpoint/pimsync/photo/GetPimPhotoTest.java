package com.jbraddockm.link.endpoint.pimsync.photo;

import static com.jbraddockm.link.testing.Steps.sent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.CgiRequest;
import com.jbraddockm.link.transport.cgi.FormField;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for GetPimPhoto: the request that streams a contact photo to a file. */
@DisplayName("GetPimPhoto: the request that fetches one photo")
class GetPimPhotoTest {

  private static CgiRequest.Download requestOf(GetPimPhoto command) {
    return (CgiRequest.Download) sent(command.conversation().start());
  }

  @Test
  @DisplayName("asks for get photos with the workstation and the path, and streams to the file")
  void asksForThePhoto() {
    var destination = Path.of("out", "photo.jpg");
    var request =
        requestOf(new GetPimPhoto("WS1", "db/contacts/7/photo", destination, Optional.empty()));

    assertThat(request.fields())
        .containsEntry(FormField.COMMAND, "get")
        .containsEntry(FormField.SERVICE, "photos")
        .containsEntry(FormField.WORKSTATION_ID, "WS1")
        .containsEntry(FormField.PATH, "db/contacts/7/photo")
        .doesNotContainKey(FormField.UPDATED_TIME);
    assertThat(request.destination()).isEqualTo(destination);
    assertThat(request.describe()).isEqualTo("Fetching a contact photo");
  }

  @Test
  @DisplayName("adds the updated time when the fetch is conditional")
  void addsUpdatedTime() {
    var request =
        requestOf(
            new GetPimPhoto("WS1", "p", Path.of("photo.jpg"), Optional.of("2026-10-29 12:00:00")));

    assertThat(request.fields()).containsEntry(FormField.UPDATED_TIME, "2026-10-29 12:00:00");
  }

  @Test
  @DisplayName("only reads, so it may be retried")
  void isReadOnly() {
    assertThat(new GetPimPhoto("WS1", "p", Path.of("photo.jpg"), Optional.empty()).readOnly())
        .isTrue();
  }

  @ParameterizedTest(name = "\"{0}\" is not a workstation")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a workstation that is not blank, naming workstationId")
  void needsAWorkstation(String workstation) {
    assertThatThrownBy(
            () -> new GetPimPhoto(workstation, "p", Path.of("photo.jpg"), Optional.empty()))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("workstationId"));
  }

  @ParameterizedTest(name = "\"{0}\" is not a path")
  @NullAndEmptySource
  @ValueSource(strings = {" "})
  @DisplayName("needs a path that is not blank, naming path")
  void needsAPath(String path) {
    assertThatThrownBy(() -> new GetPimPhoto("WS1", path, Path.of("photo.jpg"), Optional.empty()))
        .isInstanceOfSatisfying(
            InvalidArgumentException.class,
            invalid -> assertThat(invalid.parameter()).isEqualTo("path"));
  }

  @Test
  @DisplayName("needs a destination and an updated-time holder")
  void needsADestination() {
    assertThatNullPointerException()
        .isThrownBy(() -> new GetPimPhoto("WS1", "p", null, Optional.empty()))
        .withMessage("destination");
    assertThatNullPointerException()
        .isThrownBy(() -> new GetPimPhoto("WS1", "p", Path.of("photo.jpg"), null))
        .withMessage("updatedTime");
  }
}
