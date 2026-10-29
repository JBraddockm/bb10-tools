package com.jbraddockm.link.endpoint.backup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("BbbArchive: writing and reading a .bbb container")
class BbbArchiveTest {

  @TempDir private Path folder;

  private static byte[] bytes(int length) {
    var data = new byte[length];
    for (var index = 0; index < length; index++) {
      data[index] = (byte) (index * 7 + 1);
    }
    return data;
  }

  @Test
  @DisplayName("round-trips the manifest and the category archives, byte for byte")
  void roundTrip() throws Exception {
    var appBytes = bytes(1000);
    var settingsBytes = bytes(2048);
    var app = Files.write(folder.resolve("app.tar"), appBytes);
    var settings = Files.write(folder.resolve("settings.tar"), settingsBytes);
    var manifest =
        BackupManifest.of(
            "DEADBEEF",
            "10.3.3.2639",
            List.of(
                new DownloadedCategory(BackupCategory.APP, app, appBytes.length),
                new DownloadedCategory(BackupCategory.SETTINGS, settings, settingsBytes.length)),
            List.of());
    var out = folder.resolve("backup.bbb");

    BbbArchive.write(
        out,
        manifest,
        List.of(
            new BbbArchive.ArchiveEntry("Archive/app.tar", app),
            new BbbArchive.ArchiveEntry("Archive/settings.tar", settings)));

    var read = BbbArchive.readManifest(out);
    assertThat(read.pin()).isEqualTo("DEADBEEF");
    assertThat(read.softwareVersion()).isEqualTo("10.3.3.2639");
    assertThat(read.archives())
        .extracting(BackupManifest.Entry::category)
        .containsExactly(BackupCategory.APP, BackupCategory.SETTINGS);

    var extracted = folder.resolve("extracted-settings.tar");
    var size = BbbArchive.extract(out, "Archive/settings.tar", extracted);
    assertThat(size).isEqualTo(settingsBytes.length);
    assertThat(Files.readAllBytes(extracted)).isEqualTo(settingsBytes);
  }

  @Test
  @DisplayName("refuses a file that is not a .bbb")
  void refusesNonArchive() throws Exception {
    var notZip = Files.write(folder.resolve("not.bbb"), "this is not a zip".getBytes());

    assertThatThrownBy(() -> BbbArchive.readManifest(notZip))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("refuses an archive with no manifest")
  void refusesMissingManifest() throws Exception {
    var out = folder.resolve("empty.bbb");
    try (var zip = new java.util.zip.ZipOutputStream(Files.newOutputStream(out))) {
      zip.putNextEntry(new java.util.zip.ZipEntry("Archive/app.tar"));
      zip.write(bytes(10));
      zip.closeEntry();
    }

    assertThatThrownBy(() -> BbbArchive.readManifest(out))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
