package com.jbraddockm.link.endpoint.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes a {@code .bbb} archive: a ZIP holding a {@link BackupManifest#ENTRY_NAME} and one
 * {@code Archive/<category>.tar} entry per category. The tar entries are the device's own encrypted
 * archives, stored verbatim; bb-link never looks inside them.
 */
final class BbbArchive {

  private BbbArchive() {}

  /**
   * One entry to write into a {@code .bbb}: a name inside the archive and the file that fills it.
   *
   * @param name the entry name, such as {@code Archive/app.tar}
   * @param source the file whose bytes are stored under that name
   */
  record ArchiveEntry(String name, Path source) {

    /** Validates the entry. */
    ArchiveEntry {
      Objects.requireNonNull(name, "name");
      Objects.requireNonNull(source, "source");
    }
  }

  /**
   * Writes a {@code .bbb} archive: the manifest first, then each entry streamed from its file.
   *
   * @param out the file to write
   * @param manifest the manifest to store
   * @param entries the archive entries to store
   * @throws IOException if the archive cannot be written
   */
  static void write(Path out, BackupManifest manifest, List<ArchiveEntry> entries)
      throws IOException {
    try (var zip = new ZipOutputStream(Files.newOutputStream(out))) {
      zip.putNextEntry(new ZipEntry(BackupManifest.ENTRY_NAME));
      zip.write(manifest.toXml().getBytes(StandardCharsets.UTF_8));
      zip.closeEntry();
      for (var entry : entries) {
        zip.putNextEntry(new ZipEntry(entry.name()));
        Files.copy(entry.source(), zip);
        zip.closeEntry();
      }
    }
  }

  /**
   * Reads the manifest of a {@code .bbb} archive.
   *
   * @param in the archive file
   * @return the manifest
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if it is not a {@code .bbb} or the manifest is missing or bad
   */
  static BackupManifest readManifest(Path in) throws IOException {
    try (var zip = new ZipFile(in.toFile())) {
      var entry = zip.getEntry(BackupManifest.ENTRY_NAME);
      if (entry == null) {
        throw new IllegalArgumentException(
            "the file has no " + BackupManifest.ENTRY_NAME + "; it is not a bb-link backup");
      }
      try (var stream = zip.getInputStream(entry)) {
        return BackupManifest.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (ZipException malformed) {
      throw new IllegalArgumentException("the file is not a valid .bbb archive", malformed);
    }
  }

  /**
   * Extracts one entry of a {@code .bbb} archive to a file.
   *
   * @param in the archive file
   * @param entryName the entry to extract, such as {@code Archive/app.tar}
   * @param destination the file to write it to
   * @return the number of bytes written
   * @throws IOException if the file cannot be read or the destination cannot be written
   * @throws IllegalArgumentException if the entry is missing
   */
  static long extract(Path in, String entryName, Path destination) throws IOException {
    try (var zip = new ZipFile(in.toFile())) {
      var entry = zip.getEntry(entryName);
      if (entry == null) {
        throw new IllegalArgumentException("the backup has no entry " + entryName);
      }
      try (InputStream stream = zip.getInputStream(entry)) {
        return Files.copy(stream, destination);
      }
    } catch (ZipException malformed) {
      throw new IllegalArgumentException("the file is not a valid .bbb archive", malformed);
    }
  }
}
