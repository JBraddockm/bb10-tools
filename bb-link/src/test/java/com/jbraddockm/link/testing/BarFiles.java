package com.jbraddockm.link.testing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Makes small bars for the tests: a ZIP archive with a manifest, shaped like a real one. */
public final class BarFiles {

  private BarFiles() {}

  /**
   * Returns the text of a manifest: an archive section, then the package's own section.
   *
   * @param packageAttributes the lines of the package section, such as {@code Package-Type:
   *     application}
   * @return the manifest
   */
  public static String manifest(String... packageAttributes) {
    var text = new StringBuilder("Archive-Manifest-Version: 1.5\nArchive-Created-By: a test\n\n");
    for (var attribute : packageAttributes) {
      text.append(attribute).append('\n');
    }
    return text.toString();
  }

  /**
   * Writes a bar that holds a manifest and a few bytes of payload.
   *
   * @param directory where to write it
   * @param name the file name
   * @param manifestText the text of {@code META-INF/MANIFEST.MF}
   * @return the file
   * @throws IOException if it cannot be written
   */
  public static Path write(Path directory, String name, String manifestText) throws IOException {
    var file = directory.resolve(name);
    try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
      out.putNextEntry(new ZipEntry("META-INF/MANIFEST.MF"));
      out.write(manifestText.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
      out.putNextEntry(new ZipEntry("native/app"));
      out.write(new byte[] {1, 2, 3, 4});
      out.closeEntry();
    }
    return file;
  }

  /**
   * Writes a bar of an application with the given package attributes.
   *
   * @param directory where to write it
   * @param name the file name
   * @param packageId its {@code Package-Id}
   * @param version its {@code Package-Version}
   * @return the file
   * @throws IOException if it cannot be written
   */
  public static Path application(Path directory, String name, String packageId, String version)
      throws IOException {
    return write(
        directory,
        name,
        manifest(
            "Package-Type: application",
            "Package-Name: Test App",
            "Package-Id: " + packageId,
            "Package-Version: " + version,
            "Package-Architecture: armle-v7"));
  }
}
