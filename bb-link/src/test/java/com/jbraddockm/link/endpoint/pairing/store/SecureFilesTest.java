package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.link.endpoint.pairing.pki.Pem;
import com.jbraddockm.link.endpoint.pairing.pki.TestKeys;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for the restrictive file permissions applied by {@link IdentityDirectory} and {@link
 * PairingDirectory}.
 */
@DisplayName("SecureFiles: how the files of an identity or a pairing are written and moved")
class SecureFilesTest {

  private static final String OWNER_ONLY_FILE = "rw-------";
  private static final String OWNER_ONLY_FOLDER = "rwx------";

  @TempDir private Path folder;

  private static void assumePosix() {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
  }

  @Nested
  @DisplayName("withSuffix")
  class WithSuffix {

    @Test
    @DisplayName(
        "gives the name that a file has while it is written: the same folder and name, and .new")
    void givesTheTemporaryName() {
      assertThat(SecureFiles.withSuffix(Path.of("devices", "2A3B4C5D", "client_key.pem")))
          .isEqualTo(Path.of("devices", "2A3B4C5D", "client_key.pem.new"));
    }
  }

  @Nested
  @DisplayName("createPrivateDirectory")
  class CreatePrivateDirectory {

    @Test
    @DisplayName("makes the folder and the folders above it that are missing")
    void makesTheFolders() throws IOException {
      var target = folder.resolve("devices").resolve("2A3B4C5D");

      SecureFiles.createPrivateDirectory(target);

      assertThat(target).isDirectory();
    }

    @Test
    @DisplayName("makes them readable by their owner only")
    void makesThemPrivate() throws IOException {
      assumePosix();
      var target = folder.resolve("devices").resolve("2A3B4C5D");

      SecureFiles.createPrivateDirectory(target);

      assertThat(Files.getPosixFilePermissions(target))
          .isEqualTo(PosixFilePermissions.fromString(OWNER_ONLY_FOLDER));
      assertThat(Files.getPosixFilePermissions(target.getParent()))
          .isEqualTo(PosixFilePermissions.fromString(OWNER_ONLY_FOLDER));
    }

    @Test
    @DisplayName("does nothing to a folder that is there, and keeps what is in it")
    void leavesAFolderThatIsThere() throws IOException {
      Files.writeString(
          Files.createDirectories(folder.resolve("there")).resolve("kept.txt"), "kept");

      SecureFiles.createPrivateDirectory(folder.resolve("there"));

      assertThat(folder.resolve("there").resolve("kept.txt")).hasContent("kept");
    }
  }

  @Nested
  @DisplayName("writePrivateKey")
  class WritePrivateKey {

    private Path file;

    @BeforeEach
    void chooseTheFile() {
      file = folder.resolve("client_key.pem.new");
    }

    @Test
    @DisplayName("writes the key as a PRIVATE KEY block of its PKCS #8 encoding")
    void writesAPkcs8Block() throws IOException {
      var key = TestKeys.rsa().getPrivate();

      SecureFiles.writePrivateKey(file, key);

      assertThat(Pem.first(Files.readString(file), "PRIVATE KEY"))
          .hasValueSatisfying(block -> assertThat(block.content()).isEqualTo(key.getEncoded()));
    }

    @Test
    @DisplayName("writes a file that only its owner can read")
    void writesAPrivateFile() throws IOException {
      assumePosix();

      SecureFiles.writePrivateKey(file, TestKeys.rsa().getPrivate());

      assertThat(Files.getPosixFilePermissions(file))
          .isEqualTo(PosixFilePermissions.fromString(OWNER_ONLY_FILE));
    }

    @Test
    @DisplayName(
        "replaces a file that is there, with the permissions of a private file and not those of the"
            + " old one")
    void replacesAnOpenFile() throws IOException {
      assumePosix();
      Files.writeString(file, "old");
      Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-r--r--"));

      SecureFiles.writePrivateKey(file, TestKeys.ec().getPrivate());

      assertThat(Files.getPosixFilePermissions(file))
          .isEqualTo(PosixFilePermissions.fromString(OWNER_ONLY_FILE));
      assertThat(Files.readString(file)).startsWith("-----BEGIN PRIVATE KEY-----");
    }
  }

  @Nested
  @DisplayName("move")
  class Move {

    @Test
    @DisplayName("puts a file under another name, and the first name is no more")
    void movesAFile() throws IOException {
      var from = Files.writeString(folder.resolve("from.txt"), "content");
      var to = folder.resolve("to.txt");

      SecureFiles.move(from, to);

      assertThat(from).doesNotExist();
      assertThat(to).hasContent("content");
    }

    @Test
    @DisplayName("replaces a file that is there")
    void replacesAFile() throws IOException {
      var from = Files.writeString(folder.resolve("from.txt"), "new");
      var to = Files.writeString(folder.resolve("to.txt"), "old");

      SecureFiles.move(from, to);

      assertThat(to).hasContent("new");
    }
  }

  @Nested
  @DisplayName("deleteTemporaries")
  class DeleteTemporaries {

    @Test
    @DisplayName("deletes the temporary file of each file named, and leaves the files themselves")
    void deletesOnlyTheTemporaryFiles() throws IOException {
      var first = Files.writeString(folder.resolve("first.pem"), "first");
      var second = Files.writeString(folder.resolve("second.pem"), "second");
      Files.writeString(SecureFiles.withSuffix(first), "temporary");
      Files.writeString(SecureFiles.withSuffix(second), "temporary");

      SecureFiles.deleteTemporaries(List.of(first, second));

      assertThat(SecureFiles.withSuffix(first)).doesNotExist();
      assertThat(SecureFiles.withSuffix(second)).doesNotExist();
      assertThat(first).exists();
      assertThat(second).exists();
    }

    @Test
    @DisplayName(
        "does not fail for a temporary file that cannot be deleted, and goes on to the next")
    void doesNotFailForAFileThatCannotBeDeleted() throws IOException {
      var stuck = folder.resolve("stuck.pem");
      Files.writeString(
          Files.createDirectories(SecureFiles.withSuffix(stuck)).resolve("inside.txt"),
          "in a folder");
      var other = Files.writeString(folder.resolve("other.pem"), "other");
      Files.writeString(SecureFiles.withSuffix(other), "temporary");

      SecureFiles.deleteTemporaries(List.of(stuck, other));

      assertThat(SecureFiles.withSuffix(stuck)).isDirectory();
      assertThat(SecureFiles.withSuffix(other)).doesNotExist();
    }

    @Test
    @DisplayName("does not mind a temporary file that is not there")
    void doesNotMindAMissingFile() {
      SecureFiles.deleteTemporaries(List.of(folder.resolve("nothing.pem")));

      assertThat(folder).isEmptyDirectory();
    }
  }
}
