package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.transport.cgi.CgiResponse;
import com.jbraddockm.link.transport.cgi.ResponseEnvelope;
import com.jbraddockm.link.transport.cgi.Step;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("BackupStreamConversation: select, prepare, download, finish")
class BackupStreamConversationTest {

  private static final Path FILE = Path.of("settings.tar");

  private BackupStreamConversation settingsBackup() {
    return new BackupStreamConversation(
        List.of(new CategoryTarget(BackupCategory.SETTINGS, FILE)), Duration.ofMillis(1));
  }

  private static Step<BackupStreamResult> xml(BackupStreamConversation c, String body) {
    return c.onAnswer(
        new CgiResponse(200, "text/xml", body, null), ResponseEnvelope.parse(body));
  }

  private static Step<BackupStreamResult> downloaded(BackupStreamConversation c, long bytes) {
    return c.onAnswer(
        new CgiResponse(200, "application/octet-stream", "", null, new CgiResponse.Downloaded(FILE, bytes)),
        null);
  }

  @Test
  @DisplayName("drives a whole backup to a finish that lists the downloaded category")
  void wholeBackup() {
    var c = settingsBackup();
    c.start();

    assertThat(xml(c, "<RimTabletResponse><BackupStart><Status>Success</Status></BackupStart></RimTabletResponse>"))
        .isInstanceOf(Step.Send.class);
    assertThat(
            xml(
                c,
                "<RimTabletResponse><BackupStartActivity><Status>InProgress</Status></BackupStartActivity></RimTabletResponse>"))
        .isInstanceOf(Step.Send.class);
    assertThat(
            xml(
                c,
                "<RimTabletResponse><BackupStartActivity><Status>Ready</Status><Settings>4096</Settings><TotalSize>4096</TotalSize></BackupStartActivity></RimTabletResponse>"))
        .isInstanceOf(Step.Send.class);
    assertThat(downloaded(c, 4096)).isInstanceOf(Step.Send.class);

    var end =
        xml(c, "<RimTabletResponse><BackupEnd><Status>Success</Status></BackupEnd></RimTabletResponse>");
    assertThat(end).isInstanceOf(Step.Finish.class);
    var result = ((Step.Finish<BackupStreamResult>) end).result();
    assertThat(result.categories()).extracting(DownloadedCategory::category)
        .containsExactly(BackupCategory.SETTINGS);
    assertThat(result.categories().getFirst().bytes()).isEqualTo(4096);
    assertThat(result.totalSize()).isEqualTo(4096);
  }

  @Test
  @DisplayName("an empty archive fails with NO_BACKUP_DATA")
  void emptyArchiveFails() {
    var c = settingsBackup();
    c.start();
    xml(c, "<RimTabletResponse><BackupStart><Status>Success</Status></BackupStart></RimTabletResponse>");
    xml(
        c,
        "<RimTabletResponse><BackupStartActivity><Status>Ready</Status><TotalSize>0</TotalSize></BackupStartActivity></RimTabletResponse>");

    var step = downloaded(c, 0);

    assertThat(step).isInstanceOf(Step.Fail.class);
    assertThat(((Step.Fail<BackupStreamResult>) step).error().code())
        .isEqualTo(LinkErrorCode.NO_BACKUP_DATA);
  }

  @Test
  @DisplayName("an error envelope on the data step is a failure, carrying the device's error id")
  void errorOnDataStep() {
    var c = settingsBackup();
    c.start();
    xml(c, "<RimTabletResponse><BackupStart><Status>Success</Status></BackupStart></RimTabletResponse>");
    xml(
        c,
        "<RimTabletResponse><BackupStartActivity><Status>Ready</Status><TotalSize>4096</TotalSize></BackupStartActivity></RimTabletResponse>");

    var step =
        xml(
            c,
            "<RimTabletResponse><BackupGet><Status>Error</Status><Error><ErrorIdentifier>515</ErrorIdentifier><ErrorDescription>stale cookie</ErrorDescription></Error></BackupGet></RimTabletResponse>");

    assertThat(step).isInstanceOf(Step.Fail.class);
    assertThat(((Step.Fail<BackupStreamResult>) step).error().details())
        .containsEntry("errorIdentifier", 515);
  }

  @Test
  @DisplayName("fails when the device refuses the start")
  void startRefused() {
    var c = settingsBackup();
    c.start();

    var step =
        xml(
            c,
            "<RimTabletResponse><BackupStart><Status>Error</Status><Error><ErrorIdentifier>8</ErrorIdentifier></Error></BackupStart></RimTabletResponse>");

    assertThat(step).isInstanceOf(Step.Fail.class);
  }
}
