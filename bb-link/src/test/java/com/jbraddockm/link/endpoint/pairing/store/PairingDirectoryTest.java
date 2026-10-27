package com.jbraddockm.link.endpoint.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.link.endpoint.pairing.pki.Pem;
import com.jbraddockm.link.endpoint.pairing.pki.TestKeys;
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

/** Tests for {@link PairingDirectory}: the folder that holds a pairing. */
@DisplayName("PairingDirectory: the folder of a pairing with a device")
class PairingDirectoryTest {

  private static final StoredPairing PAIRING =
      new StoredPairing(
          "01AB", "PC issuer 0000000000000000", Instant.parse("2026-10-18T10:00:00Z"));

  @TempDir private Path devices;
  private PairingDirectory directory;
  private KeyPair keys;
  private String authority;
  private String deviceAuthority;

  @BeforeEach
  void prepareTheDirectory() {
    directory = PairingDirectory.of(devices, "2A3B4C5D");
    keys = TestKeys.ec();
    authority = Resources.text(TestKeys.class, "certificate--device-authority-ec.pem");
    deviceAuthority = Resources.text(TestKeys.class, "certificate--device-ca.pem");
  }

  private static void assumePosix() {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
  }

  @Nested
  @DisplayName("of")
  class Naming {

    @Test
    @DisplayName("is the folder of the PIN in the folder of the devices, with the four files in it")
    void isTheFolderOfThePin() {
      assertThat(directory.directory()).isEqualTo(devices.resolve("2A3B4C5D"));
      assertThat(directory.authorityFile())
          .isEqualTo(devices.resolve("2A3B4C5D/authority_cert.pem"));
      assertThat(directory.keyFile()).isEqualTo(devices.resolve("2A3B4C5D/authority_key.pem"));
      assertThat(directory.deviceAuthorityFile())
          .isEqualTo(devices.resolve("2A3B4C5D/device_authority.pem"));
      assertThat(directory.metadataFile()).isEqualTo(devices.resolve("2A3B4C5D/pairing.json"));
    }

    @ParameterizedTest(name = "\"{0}\" is not a PIN")
    @ValueSource(strings = {"", "../x", "a/b", "a b", "0123456789ABCDEF0", "é", "a.b"})
    @DisplayName(
        "refuses a name that is not a PIN, which keeps a PIN from leading out of the folder")
    void refusesWhatIsNotAPin(String pin) {
      assertThatIllegalArgumentException()
          .isThrownBy(() -> PairingDirectory.of(devices, pin))
          .withMessageContaining("not a PIN");
    }

    @Test
    @DisplayName("is beside the identity of the same device: one folder holds both")
    void sharesTheFolderOfTheIdentity() {
      assertThat(directory.directory())
          .isEqualTo(IdentityDirectory.of(devices, "2A3B4C5D").directory());
    }
  }

  @Nested
  @DisplayName("existingFiles and metadata")
  class Reading {

    @Test
    @DisplayName("existingFiles is empty before anything is written")
    void nothingIsThereAtFirst() {
      assertThat(directory.existingFiles()).isEmpty();
    }

    @Test
    @DisplayName(
        "existingFiles lists the files that are there, in a fixed order, and not those of the"
            + " identity")
    void listsOnlyItsOwnFiles() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.metadataFile(), "m");
      Files.writeString(directory.authorityFile(), "a");
      Files.writeString(directory.directory().resolve("client_cert.pem"), "c");

      assertThat(directory.existingFiles())
          .containsExactly(directory.authorityFile(), directory.metadataFile());
    }

    @Test
    @DisplayName("metadata gives nothing when there is no file, and reads one that is there")
    void metadataReadsTheFile() throws IOException {
      assertThat(directory.metadata()).isEmpty();
      Files.createDirectories(directory.directory());
      Files.writeString(directory.metadataFile(), PAIRING.toJsonText());

      assertThat(directory.metadata()).contains(PAIRING);
    }
  }

  @Nested
  @DisplayName("prepare")
  class Preparing {

    @Test
    @DisplayName("makes the folder and writes the key under a temporary name, and nothing else")
    void writesTheKeyUnderATemporaryName() throws IOException {
      try (var _ = directory.prepare(keys, false)) {
        assertThat(SecureFiles.withSuffix(directory.keyFile())).exists();
        assertThat(directory.existingFiles()).isEmpty();
      }
    }

    @Test
    @DisplayName("writes the key where only its owner can read it")
    void keepsTheKeyPrivate() throws IOException {
      assumePosix();

      try (var _ = directory.prepare(keys, false)) {
        assertThat(Files.getPosixFilePermissions(SecureFiles.withSuffix(directory.keyFile())))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
      }
    }

    @Test
    @DisplayName("refuses to replace a pairing that is there, and names it")
    void refusesToReplace() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.authorityFile(), "old");

      assertThatThrownBy(() -> directory.prepare(keys, false))
          .isInstanceOf(FilesExistException.class)
          .hasMessageContaining("a pairing with this device")
          .hasMessageContaining("authority_cert.pem");
      assertThat(directory.authorityFile()).hasContent("old");
    }

    @Test
    @DisplayName("is not stopped by the files of the identity in the same folder")
    void theIdentityDoesNotStopIt() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.directory().resolve("client_cert.pem"), "identity");

      try (var _ = directory.prepare(keys, false)) {
        assertThat(directory.directory().resolve("client_cert.pem")).hasContent("identity");
      }
    }
  }

  @Nested
  @DisplayName("commit")
  class Committing {

    @Test
    @DisplayName("moves the key into place and writes the two authorities and the pairing file")
    void movesTheKeyAndWritesTheRest() throws IOException {
      try (var pending = directory.prepare(keys, false)) {
        pending.commit(authority, deviceAuthority, PAIRING);
      }

      assertThat(directory.existingFiles())
          .containsExactly(
              directory.authorityFile(),
              directory.keyFile(),
              directory.deviceAuthorityFile(),
              directory.metadataFile());
      assertThat(directory.authorityFile()).hasContent(authority);
      assertThat(directory.deviceAuthorityFile()).hasContent(deviceAuthority);
      assertThat(directory.metadataFile()).hasContent(PAIRING.toJsonText());
      assertThat(Pem.first(Files.readString(directory.keyFile()), "PRIVATE KEY"))
          .hasValueSatisfying(
              block -> assertThat(block.content()).isEqualTo(keys.getPrivate().getEncoded()));
    }

    @Test
    @DisplayName("leaves no temporary file behind, and keeps the key private")
    void leavesNoTemporaryFile() throws IOException {
      try (var pending = directory.prepare(keys, false)) {
        pending.commit(authority, deviceAuthority, PAIRING);
      }

      try (var names = Files.list(directory.directory())) {
        assertThat(names.map(path -> path.getFileName().toString()))
            .noneMatch(name -> name.endsWith(".new"));
      }
      if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
        assertThat(Files.getPosixFilePermissions(directory.keyFile()))
            .isEqualTo(PosixFilePermissions.fromString("rw-------"));
      }
    }

    @Test
    @DisplayName("replaces a pairing that was there, when it was allowed")
    void replacesTheOldPairing() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.authorityFile(), "old");

      try (var pending = directory.prepare(keys, true)) {
        pending.commit(authority, deviceAuthority, PAIRING);
      }

      assertThat(directory.authorityFile()).hasContent(authority);
    }
  }

  @Nested
  @DisplayName("close")
  class Closing {

    @Test
    @DisplayName(
        "takes back the temporary key when nothing was committed, and keeps the pairing that was"
            + " there")
    void takesBackWhatWasNotCommitted() throws IOException {
      Files.createDirectories(directory.directory());
      Files.writeString(directory.authorityFile(), "old");

      try (var _ = directory.prepare(keys, true)) {
        assertThat(SecureFiles.withSuffix(directory.keyFile())).exists();
      }

      assertThat(SecureFiles.withSuffix(directory.keyFile())).doesNotExist();
      assertThat(directory.authorityFile()).hasContent("old");
    }

    @Test
    @DisplayName("leaves what was committed")
    void leavesWhatWasCommitted() throws IOException {
      try (var pending = directory.prepare(keys, false)) {
        pending.commit(authority, deviceAuthority, PAIRING);
      }

      assertThat(directory.existingFiles()).hasSize(4);
    }
  }
}
