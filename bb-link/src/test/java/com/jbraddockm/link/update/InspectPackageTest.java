package com.jbraddockm.link.update;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.core.error.CoreErrorCode;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link InspectPackage}: inspecting a bar, with optional validation. */
@DisplayName("InspectPackage: looking at a bar")
class InspectPackageTest {

  @TempDir private Path folder;

  @Test
  @DisplayName("gives what the bar says of itself, and no validation unless it was asked for")
  void givesWhatTheBarSays() {
    var inspection =
        succeeded(
            InspectPackage.inspect(BarResources.copy("application--first.bar", folder), false));

    assertThat(inspection.info().packageId()).isEqualTo("idFirst");
    assertThat(inspection.validation()).isNull();
  }

  @Test
  @DisplayName("gives the validation too when it was asked for")
  void givesTheValidation() {
    var inspection =
        succeeded(
            InspectPackage.inspect(BarResources.copy("application--first.bar", folder), true));

    assertThat(inspection.validation()).isNotNull();
    assertThat(inspection.toJson()).containsKey("validation");
  }

  @Test
  @DisplayName("fails as an invalid argument for a file that is not a usable bar, naming file")
  void failsForAFileThatIsNotABar() {
    var failure = failed(InspectPackage.inspect(BarResources.copy("not-a-bar.txt", folder), false));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
    assertThat(failure.error().message()).contains("is not a usable bar: ");
    assertThat(failure.error().details()).containsEntry("param", "file");
  }

  @Test
  @DisplayName("fails as an error of reading for a file that is not there, naming it")
  void failsForAMissingFile() {
    var missing = folder.resolve("missing.bar");

    var failure = failed(InspectPackage.inspect(missing, false));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(failure.error().message()).isEqualTo("The file does not exist");
    assertThat(failure.error().details()).containsEntry("file", missing.toString());
  }

  @Test
  @DisplayName(
      "fails as an error of reading for a file that cannot be read, which says that it cannot be"
          + " read and not that it does not exist")
  void failsForAFileThatCannotBeRead() throws Exception {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    var file = BarResources.copy("application--first.bar", folder);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
    assumeFalse(Files.isReadable(file), "running as a user that can read everything");

    var failure = failed(InspectPackage.inspect(file, false));

    assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(failure.error().message()).startsWith("The file cannot be read: ");
  }
}
