package com.jbraddockm.link.endpoint.backup;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.command.ProgressPhase;
import com.jbraddockm.link.LinkErrorCode;
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

@DisplayName("Backing up through a session against the fake device")
class BackupSessionTest {

  @TempDir private Path folder;
  private FakeDevice device;
  private DeviceSession session;

  private static byte[] bytes(int length, int seed) {
    var data = new byte[length];
    for (var index = 0; index < length; index++) {
      data[index] = (byte) (index * 31 + seed);
    }
    return data;
  }

  @BeforeEach
  void start() throws Exception {
    device = new FakeDevice("secret");
    device.backupCategoryBytes.put("app", bytes(300 * 1024, 1));
    device.backupCategoryBytes.put("media", bytes(50 * 1024, 2));
    device.backupCategoryBytes.put("settings", bytes(4096, 3));
    session =
        DeviceSession.forTarget(
            new DeviceTarget(
                InetAddress.getLoopbackAddress().getHostAddress(),
                device.port(),
                TransportScheme.HTTP,
                new Authentication.Password("secret")));
  }

  @AfterEach
  void close() {
    session.close();
    device.close();
  }

  @Test
  @DisplayName("backs up every category by default, and the .bbb holds each archive byte for byte")
  void backsUpAll() throws Exception {
    var out = folder.resolve("all.bbb");

    var result =
        succeeded(new DeviceBackup().run(session, BackupRequest.of(out, List.of()), new Logs()));

    assertThat(Files.exists(out)).isTrue();
    assertThat(result.categoryBytes().keySet()).containsExactly("app", "media", "settings");
    var manifest = BbbArchive.readManifest(out);
    for (var category : List.of("app", "media", "settings")) {
      var extracted = folder.resolve("x-" + category + ".tar");
      BbbArchive.extract(out, "Archive/" + category + ".tar", extracted);
      assertThat(Files.readAllBytes(extracted)).isEqualTo(device.backupCategoryBytes.get(category));
    }
    assertThat(manifest.archives()).hasSize(3);
  }

  @Test
  @DisplayName("backs up only the chosen category")
  void backsUpSelected() throws Exception {
    var out = folder.resolve("settings.bbb");

    succeeded(
        new DeviceBackup()
            .run(session, BackupRequest.of(out, List.of(BackupCategory.SETTINGS)), new Logs()));

    assertThat(BbbArchive.readManifest(out).archives())
        .extracting(BackupManifest.Entry::category)
        .containsExactly(BackupCategory.SETTINGS);
    assertThat(device.backupRequests).anyMatch(line -> line.equals("backup start mode=settings"));
  }

  @Test
  @DisplayName("reports progress for the download and the packaging")
  void reportsProgress() {
    var logs = new Logs();

    succeeded(
        new DeviceBackup()
            .run(session, BackupRequest.of(folder.resolve("p.bbb"), List.of()), logs));

    var phases =
        logs.progress().stream().map(Progress::phase).map(ProgressPhase::wireName).toList();
    assertThat(phases).contains("downloading", "packaging");
  }

  @Test
  @DisplayName("an empty archive from the device fails with NO_BACKUP_DATA")
  void emptyArchiveFails() {
    device.backupCategoryBytes.remove("settings");

    var failure =
        failed(
            new DeviceBackup()
                .run(
                    session,
                    BackupRequest.of(folder.resolve("x.bbb"), List.of(BackupCategory.SETTINGS)),
                    new Logs()));

    assertThat(failure.error().code()).isEqualTo(LinkErrorCode.NO_BACKUP_DATA);
  }

  @Test
  @DisplayName("a package-type sub-selection backs up just that type and records pkgtype")
  void backsUpByPackageType() throws Exception {
    var dataBytes = bytes(65536, 9);
    device.backupCategoryBytes.put("app:data", dataBytes);
    var out = folder.resolve("app-data.bbb");

    succeeded(
        new DeviceBackup()
            .run(
                session,
                new BackupRequest(out, List.of(BackupCategory.APP), List.of(PackageType.DATA)),
                new Logs()));

    var extracted = folder.resolve("x-app.tar");
    BbbArchive.extract(out, "Archive/app.tar", extracted);
    assertThat(Files.readAllBytes(extracted)).isEqualTo(dataBytes);
    assertThat(BbbArchive.readManifest(out).archives())
        .anySatisfy(
            entry -> {
              assertThat(entry.category()).isEqualTo(BackupCategory.APP);
              assertThat(entry.pkgtype()).isEqualTo("data");
            });
    assertThat(device.backupRequests).anyMatch(line -> line.equals("backup type=app pkgtype=data"));
  }
}
