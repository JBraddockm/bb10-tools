package com.jbraddockm.link.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

/** Files that checks make and must remove. */
public final class TestFiles {

  private TestFiles() {}

  /**
   * Deletes a folder and everything in it.
   *
   * @param root the folder; nothing happens if it is not there
   * @throws IOException if something cannot be deleted
   */
  public static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      for (var path : paths.sorted(Comparator.reverseOrder()).toList()) {
        Files.delete(path);
      }
    }
  }
}
