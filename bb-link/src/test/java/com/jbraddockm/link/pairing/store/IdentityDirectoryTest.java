package com.jbraddockm.link.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.pki.TestKeys;
import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyPair;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link IdentityDirectory}: the folder that holds the certificate, key and identity of a
 * device.
 */
@DisplayName("IdentityDirectory: the folder of the certificate of a device")
class IdentityDirectoryTest {

  private static final StoredIdentity IDENTITY =
      new StoredIdentity("BBCGIAUTH [2A3B4C5D] workstation", Instant.parse("2026-10-18T10:00:00Z"));

  @TempDir private Path devices;
  private IdentityDirectory directory;
  private KeyPair keys;
  private String certificate;

  @BeforeEach
  void prepareTheDirectory() {
    directory = IdentityDirectory.of(devices, "2A3B4C5D");
    keys = TestKeys.rsa();
    certificate = Resources.text(TestKeys.class, "certificate--device-issued.pem");
  }

  private static void assumePosix() {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
  }

  @Nested
  @DisplayName("of")
  class Naming {

    @Test
    @DisplayName(
        "is the folder of the PIN in the folder of the devices, with the three files in it")
    void isTheFolderOfThePin() {
      assertThat(directory.directory()).isEqualTo(devices.resolve("2A3B4C5D"));
      assertThat(directory.certificateFile())
          .isEqualTo(devices.resolve("2A3B4C5D/client_cert.pem"));
      assertThat(directory.keyFile()).isEqualTo(devices.resolve("2A3B4C5D/client_key.pem"));
      assertThat(directory.metadataFile()).isEqualTo(devices.resolve("2A3B4C5D/identity.json"));
    }

    @ParameterizedTest(name = "\"{0}\" is a PIN")
    @ValueSource(strings = {"2A3B4C5D", "a", "0123456789ABCDEF", "abcdef12"})
    @DisplayName("takes 1 to 16 letters and digits as a PIN")
    void takesLettersAndDigits(String pin) {
      assertThat(IdentityDirectory.of(devices, pin).directory()).isEqualTo(devices.resolve(pin));
    }

    @ParameterizedTest(name = "\"{0}\" is not a PIN")
    @ValueSource(strings = {"", "../x", "a/b", "a b", "0123456789ABCDEF0", "é", "a.b"})
    @DisplayName(
        "refuses a name that is not a PIN, which keeps a PIN from leading out of the folder")
    void refusesWhatIsNotAPin(String pin) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> IdentityDirectory.of(devices, pin))
          .withMessageContaining("not a PIN");
    }
  }

  @Nested
  @DisplayName("existingFiles and metadata")
  class Reading {

    @Test
    @DisplayName("existingFiles is empty before anything is written")
    void nothingIsThereAtFirst() {
      assertThat(directory.existingFiles()).isEmpty();
      assertThat(directory.directory()).doesNotExist();
    }

    @Test
    @DisplayName(
        "existingFiles lists the files that are there, in a fixed order, whichever was written"
            + " first")
    void listsTheFilesInAFixedOrder() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.metadataFile(), "m");
      Files.writeString(directory.certificateFile(), "c");

      assertThat(directory.existingFiles())
          .containsExactly(directory.certificateFile(), directory.metadataFile());
    }

    @Test
    @DisplayName("metadata gives nothing when there is no file")
    void metadataIsEmptyWithoutAFile() throws IOException {
      assertThat(directory.metadata()).isEmpty();
    }

    @Test
    @DisplayName("metadata reads the file")
    void metadataReadsTheFile() throws IOException {
      Files.createDirectories(directory.directory());
      Resources.copyTo(
          StoredIdentityTest.class, "stored-identity--complete.json", directory.directory());
      Files.move(
          directory.directory().resolve("stored-identity--complete.json"),
          directory.metadataFile());

      assertThat(directory.metadata()).contains(IDENTITY);
    }

    @Test
    @DisplayName("metadata fails for a file that is not an identity file")
    void metadataFailsForAnotherFile() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.metadataFile(), "{ not json");

      assertThatIllegalArgumentException()
          .isThrownBy(() -> directory.metadata())
          .withMessageContaining("not readable");
    }
  }

  @Nested
  @DisplayName("prepare")
  class Preparing {

    @Test
    @DisplayName("makes the folder and writes the key under a temporary name, and nothing else")
    void writesTheKeyUnderATemporaryName() throws IOException {
      try (var _ = directory.prepare(keys, IDENTITY, false)) {
        assertThat(directory.directory()).isDirectory();
        assertThat(SecureFiles.withSuffix(directory.keyFile())).exists();
        assertThat(directory.existingFiles()).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "writes the key where only its owner can read it, and makes the folder private too")
    void keepsTheKeyPrivate() throws IOException {
      assumePosix();

      try (var _ = directory.prepare(keys, IDENTITY, false)) {
        assertThat(Files.getPosixFilePermissions(SecureFiles.withSuffix(directory.keyFile())))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        assertThat(Files.getPosixFilePermissions(directory.directory()))
            .isEqualTo(PosixFilePermissions.fromString("rwx------"));
      }
    }

    @Test
    @DisplayName("refuses to replace an identity that is there, and names the folder and the files")
    void refusesToReplace() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.certificateFile(), "old");

      assertThatThrownBy(() -> directory.prepare(keys, IDENTITY, false))
          .isInstanceOf(FilesExistException.class)
          .hasMessageContaining("an identity for this device")
          .hasMessageContaining("client_cert.pem");
      assertThat(directory.certificateFile()).hasContent("old");
      assertThat(SecureFiles.withSuffix(directory.keyFile())).doesNotExist();
    }

    @Test
    @DisplayName(
        "replaces an identity that is there when it is told to, and leaves it until it is"
            + " committed")
    void replacesWhenToldTo() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.certificateFile(), "old");

      try (var _ = directory.prepare(keys, IDENTITY, true)) {
        assertThat(directory.certificateFile()).hasContent("old");
        assertThat(SecureFiles.withSuffix(directory.keyFile())).exists();
      }
    }
  }

  @Nested
  @DisplayName("commit")
  class Committing {

    @Test
    @DisplayName("moves the key into place and writes the certificate and the identity file")
    void movesTheKeyAndWritesTheRest() throws IOException {
      try (var pending = directory.prepare(keys, IDENTITY, false)) {
        pending.commit(certificate);
      }

      assertThat(directory.existingFiles())
          .containsExactly(
              directory.certificateFile(), directory.keyFile(), directory.metadataFile());
      assertThat(directory.certificateFile()).hasContent(certificate);
      assertThat(directory.metadataFile()).hasContent(IDENTITY.toJsonText());
      assertThat(Pem.first(Files.readString(directory.keyFile()), "PRIVATE KEY"))
          .hasValueSatisfying(
              block -> assertThat(block.content()).isEqualTo(keys.getPrivate().getEncoded()));
    }

    @Test
    @DisplayName("leaves no temporary file behind")
    void leavesNoTemporaryFile() throws IOException {
      try (var pending = directory.prepare(keys, IDENTITY, false)) {
        pending.commit(certificate);
      }

      try (var names = Files.list(directory.directory())) {
        assertThat(names.map(path -> path.getFileName().toString()))
            .noneMatch(name -> name.endsWith(".new"));
      }
    }

    @Test
    @DisplayName("keeps the key private after the move")
    void theKeyStaysPrivate() throws IOException {
      assumePosix();

      try (var pending = directory.prepare(keys, IDENTITY, false)) {
        pending.commit(certificate);
      }

      assertThat(Files.getPosixFilePermissions(directory.keyFile()))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    }

    @Test
    @DisplayName("writes the identity that it is given, when it is given one")
    void writesTheIdentityItIsGiven() throws IOException {
      var written = new StoredIdentity("another name", Instant.parse("2027-01-01T00:00:00Z"));

      try (var pending = directory.prepare(keys, IDENTITY, false)) {
        pending.commit(certificate, written);
      }

      assertThat(directory.metadata()).contains(written);
    }

    @Test
    @DisplayName("replaces the files of the identity that was there")
    void replacesTheOldFiles() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.certificateFile(), "old");
      Files.writeString(directory.keyFile(), "old");
      Files.writeString(directory.metadataFile(), "old");

      try (var pending = directory.prepare(keys, IDENTITY, true)) {
        pending.commit(certificate);
      }

      assertThat(directory.certificateFile()).hasContent(certificate);
      assertThat(Files.readString(directory.keyFile())).startsWith("-----BEGIN PRIVATE KEY-----");
      assertThat(directory.metadata()).contains(IDENTITY);
    }
  }

  @Nested
  @DisplayName("close")
  class Closing {

    @Test
    @DisplayName("takes back the temporary key when nothing was committed, and writes nothing else")
    void takesBackWhatWasNotCommitted() throws IOException {
      try (var _ = directory.prepare(keys, IDENTITY, false)) {
        assertThat(SecureFiles.withSuffix(directory.keyFile())).exists();
      }

      assertThat(SecureFiles.withSuffix(directory.keyFile())).doesNotExist();
      assertThat(directory.existingFiles()).isEmpty();
    }

    @Test
    @DisplayName("leaves the files of an identity that was replaced and not committed as they were")
    void leavesTheOldIdentityAlone() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.certificateFile(), "old");

      try (var _ = directory.prepare(keys, IDENTITY, true)) {
        // the device refused: nothing is committed
      }

      assertThat(directory.certificateFile()).hasContent("old");
      assertThat(SecureFiles.withSuffix(directory.keyFile())).doesNotExist();
    }

    @Test
    @DisplayName("leaves what was committed")
    void leavesWhatWasCommitted() throws IOException {
      try (var pending = directory.prepare(keys, IDENTITY, false)) {
        pending.commit(certificate);
      }

      assertThat(directory.existingFiles()).hasSize(3);
    }
  }
}
