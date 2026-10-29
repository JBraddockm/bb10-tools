package com.jbraddockm.link.endpoint.backup;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.transport.cgi.Authentication;
import com.jbraddockm.link.transport.cgi.DeviceTarget;
import com.jbraddockm.link.transport.cgi.TransportScheme;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Restoring through a session against the fake device")
class RestoreSessionTest {

  @TempDir private Path folder;
  private FakeDevice device;
  private DeviceSession session;
  private byte[] appBytes;
  private byte[] settingsBytes;
  private Path archive;

  private static byte[] bytes(int length, int seed) {
    var data = new byte[length];
    for (var index = 0; index < length; index++) {
      data[index] = (byte) (index * 17 + seed);
    }
    return data;
  }

  @BeforeEach
  void start() throws Exception {
    device = new FakeDevice("secret");
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                InetAddress.getLoopbackAddress().getHostAddress(),
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")));
    appBytes = bytes(2048, 1);
    settingsBytes = bytes(4096, 2);
    var app = Files.write(folder.resolve("app.tar"), appBytes);
    var settings = Files.write(folder.resolve("settings.tar"), settingsBytes);
    archive = folder.resolve("backup.bbb");
    BbbArchive.write(
        archive,
        BackupManifest.of(
            "DEADBEEF",
            "10.3.3",
            List.of(
                new DownloadedCategory(BackupCategory.APP, app, appBytes.length),
                new DownloadedCategory(BackupCategory.SETTINGS, settings, settingsBytes.length)),
            List.of()),
        List.of(
            new BbbArchive.ArchiveEntry("Archive/app.tar", app),
            new BbbArchive.ArchiveEntry("Archive/settings.tar", settings)));
  }

  @AfterEach
  void close() {
    session.close();
    device.close();
  }

  @Test
  @DisplayName("without confirmation, nothing is sent to the device")
  void refusesWithoutConfirmation() {
    var failure =
        failed(
            new DeviceRestore()
                .run(session, new RestoreRequest(archive, List.of(), false), new Logs()));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
    assertThat(device.cgiRequests.get()).isZero();
    assertThat(device.loginRequests.get()).isZero();
  }

  @Test
  @DisplayName("uploads every category's bytes to the device and reports the reset")
  void restoresAll() {
    var result =
        succeeded(
            new DeviceRestore()
                .run(session, new RestoreRequest(archive, List.of(), true), new Logs()));

    assertThat(result.restored()).containsExactly(BackupCategory.APP, BackupCategory.SETTINGS);
    assertThat(result.resetRequired()).isTrue();
    assertThat(device.restoreUploads.get("app")).isEqualTo(appBytes);
    assertThat(device.restoreUploads.get("settings")).isEqualTo(settingsBytes);
  }

  @Test
  @DisplayName("restores only the chosen category")
  void restoresSelected() {
    succeeded(
        new DeviceRestore()
            .run(
                session,
                new RestoreRequest(archive, List.of(BackupCategory.SETTINGS), true),
                new Logs()));

    assertThat(device.restoreUploads).containsOnlyKeys("settings");
    assertThat(device.restoreUploads.get("settings")).isEqualTo(settingsBytes);
  }
}
