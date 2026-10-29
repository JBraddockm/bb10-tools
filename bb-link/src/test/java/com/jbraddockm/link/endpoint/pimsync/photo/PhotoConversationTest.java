package com.jbraddockm.link.endpoint.pimsync.photo;

import static com.jbraddockm.link.testing.Steps.failed;
import static com.jbraddockm.link.testing.Steps.finished;
import static com.jbraddockm.link.testing.Steps.partial;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.Step;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for the photo conversation: how a contact photo's answer — image or status — is made. */
@DisplayName("PhotoConversation: the answer to a contact-photo request")
class PhotoConversationTest {

  private static Conversation<PimPhoto> fetch() {
    return new GetPimPhoto("WS1", "p", Path.of("out.jpg"), Optional.empty()).conversation();
  }

  private static Step<PimPhoto> answerTo(
      Conversation<PimPhoto> conversation, CgiResponse response) {
    return conversation.onAnswer(response, null);
  }

  @Test
  @DisplayName("a streamed image is the result: the file and its size, and no status code")
  void aStreamedImage() {
    var response =
        new CgiResponse(
            200, "image/jpeg", "", null, new CgiResponse.Downloaded(Path.of("out.jpg"), 2048));

    var result = finished(answerTo(fetch(), response));

    assertThat(result.file()).contains(Path.of("out.jpg"));
    assertThat(result.bytes()).isEqualTo(2048);
    assertThat(result.contentType()).isEqualTo("image/jpeg");
    assertThat(result.code()).isNull();
  }

  @Test
  @DisplayName("1016 No Changes is a result with that code and no file, not a refusal")
  void noChanges() {
    var body = Resources.text(PhotoConversationTest.class, "photo--no-changes.json");
    var response = new CgiResponse(200, "application/json", body, null);

    var result = finished(answerTo(fetch(), response));

    assertThat(result.file()).isEmpty();
    assertThat(result.code()).isEqualTo(1016);
    assertThat(result.message()).isEqualTo("No Changes");
  }

  @Test
  @DisplayName("1013 unauthorised fails the command and keeps the status that came")
  void unauthorised() {
    var body = Resources.text(PhotoConversationTest.class, "photo--unauthorized.json");
    var step = answerTo(fetch(), new CgiResponse(200, "application/json", body, null));

    var error = failed(step);
    assertThat(error.code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
    assertThat(error.message()).isEqualTo("received HTTP 401/403 from server");
    assertThat(partial(step)).hasValueSatisfying(kept -> assertThat(kept.code()).isEqualTo(1013));
  }
}
