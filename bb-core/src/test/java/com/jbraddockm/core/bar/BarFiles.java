package com.jbraddockm.core.bar;

import com.jbraddockm.core.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Creates {@code .bar} archives for the inspector tests: a ZIP with a given manifest or given
 * entries.
 */
final class BarFiles {

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";

  private BarFiles() {}

  /**
   * An archive that has a manifest, from a resource.
   *
   * @param folder where to make it
   * @param fileName the name of the archive
   * @param manifestResource the name of the resource that holds the manifest
   * @return the archive
   * @throws IOException if it cannot be written
   */
  static Path withManifest(Path folder, String fileName, String manifestResource)
      throws IOException {
    return withEntries(
        folder,
        fileName,
        Map.of(MANIFEST_ENTRY, Resources.bytes(BarFiles.class, manifestResource)));
  }

  /**
   * An archive with these entries.
   *
   * @param folder where to make it
   * @param fileName the name of the archive
   * @param entries the names of the entries and what they hold
   * @return the archive
   * @throws IOException if it cannot be written
   */
  static Path withEntries(Path folder, String fileName, Map<String, byte[]> entries)
      throws IOException {
    var file = folder.resolve(fileName);
    try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (var entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue());
        out.closeEntry();
      }
    }
    return file;
  }
}
