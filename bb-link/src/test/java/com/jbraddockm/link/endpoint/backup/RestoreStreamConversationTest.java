package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RestoreStreamConversation: begin, upload, finish")
class RestoreStreamConversationTest {

  private RestoreStreamConversation settingsRestore() {
    return new RestoreStreamConversation(
        List.of(new UploadTarget(BackupCategory.SETTINGS, Path.of("settings.tar"), 4096)), 4096);
  }

  private static Step<RestoreStreamResult> xml(RestoreStreamConversation c, String body) {
    return c.onAnswer(
        new CgiResponse(200, "text/xml", body, null), ResponseEnvelope.parse(body));
  }

  @Test
  @DisplayName("drives a whole restore to success, carrying ResetRequired")
  void wholeRestore() {
    var c = settingsRestore();
    c.start();

    assertThat(
            xml(c, "<RimTabletResponse><RestoreStart><Status>Ready</Status></RestoreStart></RimTabletResponse>"))
        .isInstanceOf(Step.Send.class);
    assertThat(
            xml(
                c,
                "<RimTabletResponse><RestoreSend><Status>Success</Status><ResetRequired>True</ResetRequired></RestoreSend></RimTabletResponse>"))
        .isInstanceOf(Step.Send.class);

    var end =
        xml(c, "<RimTabletResponse><BackupEnd><Status>Success</Status></BackupEnd></RimTabletResponse>");
    assertThat(end).isInstanceOf(Step.Finish.class);
    var result = ((Step.Finish<RestoreStreamResult>) end).result();
    assertThat(result.restored()).containsExactly(BackupCategory.SETTINGS);
    assertThat(result.resetRequired()).isTrue();
  }

  @Test
  @DisplayName("treats an empty finish answer as success, because the device resets")
  void emptyFinishIsSuccess() {
    var c = settingsRestore();
    c.start();
    xml(c, "<RimTabletResponse><RestoreStart><Status>Ready</Status></RestoreStart></RimTabletResponse>");
    xml(
        c,
        "<RimTabletResponse><RestoreSend><Status>Success</Status><ResetRequired>True</ResetRequired></RestoreSend></RimTabletResponse>");

    var end = c.onAnswer(new CgiResponse(200, null, "", null), null);

    assertThat(end).isInstanceOf(Step.Finish.class);
    assertThat(((Step.Finish<RestoreStreamResult>) end).result().resetRequired()).isTrue();
  }

  @Test
  @DisplayName("fails when the device reports an error on an upload")
  void uploadError() {
    var c = settingsRestore();
    c.start();
    xml(c, "<RimTabletResponse><RestoreStart><Status>Ready</Status></RestoreStart></RimTabletResponse>");

    var step =
        xml(
            c,
            "<RimTabletResponse><RestoreSend><Status>Error</Status><Error><ErrorIdentifier>4</ErrorIdentifier><ErrorDescription>User cancelled</ErrorDescription></Error></RestoreSend></RimTabletResponse>");

    assertThat(step).isInstanceOf(Step.Fail.class);
  }
}
