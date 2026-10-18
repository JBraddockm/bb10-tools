package com.jbraddockm.link.pairing.store;

import com.jbraddockm.link.pairing.pki.Pem;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.PrivateKey;
import java.util.List;

/** What the folders of an identity and of a pairing do alike with their files. */
final class SecureFiles {

  private static final String SUFFIX = ".new";

  private SecureFiles() {}

  /** The name that a file has while it is written, before it is moved into place. */
  static Path withSuffix(Path file) {
    return file.resolveSibling(file.getFileName() + SUFFIX);
  }

  /** Makes the folder, for its owner only, where the system has such permissions. */
  static void createPrivateDirectory(Path directory) throws IOException {
    Files.createDirectories(directory, ownerOnly(true));
  }

  /**
   * Writes a private key, as PEM, to a new file that only its owner can read: the file never exists
   * with wider permissions, not even for a moment.
   */
  static void writePrivateKey(Path file, PrivateKey key) throws IOException {
    Files.deleteIfExists(file);
    Files.writeString(
        Files.createFile(file, ownerOnly(false)),
        Pem.encode("PRIVATE KEY", key.getEncoded()),
        StandardCharsets.UTF_8);
  }

  /** Moves a file into place, atomically where the file system can. */
  static void move(Path from, Path to) throws IOException {
    try {
      Files.move(from, to, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  /** Deletes the temporary versions of files, as far as it can. */
  static void deleteTemporaries(List<Path> files) {
    for (var file : files) {
      try {
        Files.deleteIfExists(withSuffix(file));
      } catch (IOException _) {
        // Nothing more can be done about a file that cannot be deleted.
      }
    }
  }

  private static FileAttribute<?>[] ownerOnly(boolean directory) {
    if (!FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      return new FileAttribute<?>[0];
    }
    var permissions = PosixFilePermissions.fromString(directory ? "rwx------" : "rw-------");
    return new FileAttribute<?>[] {PosixFilePermissions.asFileAttribute(permissions)};
  }
}
