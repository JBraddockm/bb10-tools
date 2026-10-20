package com.jbraddockm.link.update;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.error.CoreErrorCode;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link BarSelection}: turning named files into bars, and reporting the first unusable
 * one by position.
 */
@DisplayName("BarSelection: the bars that the caller named")
class BarSelectionTest {

  @TempDir private Path folder;

  private String path(String resource) {
    return BarResources.copy(resource, folder).toString();
  }

  private static BarSelection.Refused refused(BarSelection selection) {
    assertThat(selection).isInstanceOf(BarSelection.Refused.class);
    return (BarSelection.Refused) selection;
  }

  @Test
  @DisplayName("selects the bars in the order that they were named")
  void selectsInOrder() {
    var selection =
        BarSelection.of(List.of(path("application--second.bar"), path("application--first.bar")));

    assertThat(selection)
        .isInstanceOfSatisfying(
            BarSelection.Selected.class,
            selected ->
                assertThat(selected.bars())
                    .extracting(BarInfo::packageId)
                    .containsExactly("idSecond", "idFirst"));
  }

  @Test
  @DisplayName("selects nothing when nothing was named")
  void selectsNothing() {
    assertThat(BarSelection.of(List.of()))
        .isInstanceOfSatisfying(
            BarSelection.Selected.class, selected -> assertThat(selected.bars()).isEmpty());
  }

  @Test
  @DisplayName("refuses a name that is not a path, naming its position")
  void refusesAnInvalidPath() {
    var error =
        refused(BarSelection.of(List.of(path("application--first.bar"), "bad\0name"))).error();

    assertThat(error.code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
    assertThat(error.message()).contains("is not a valid path");
    assertThat(error.details()).containsEntry("param", "files[1]");
  }

  @Test
  @DisplayName("refuses a file that is not a bar, saying why, and naming its position")
  void refusesAFileThatIsNotABar() {
    var error = refused(BarSelection.of(List.of(path("not-a-bar.txt")))).error();

    assertThat(error.code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
    assertThat(error.message()).startsWith("'files[0]' is not a usable bar: ");
    assertThat(error.details()).containsEntry("param", "files[0]");
  }

  @Test
  @DisplayName(
      "refuses a file that is not there as an error of reading, naming the position and the file")
  void refusesAMissingFile() {
    var missing = folder.resolve("missing.bar").toString();

    var error = refused(BarSelection.of(List.of(path("application--first.bar"), missing))).error();

    assertThat(error.code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(error.message()).isEqualTo("The file does not exist");
    assertThat(error.details()).containsEntry("param", "files[1]").containsEntry("file", missing);
  }

  @Test
  @DisplayName(
      "refuses a file that cannot be read as an error of reading, which says that it cannot be read"
          + " and not that it does not exist")
  void refusesAFileThatCannotBeRead() throws Exception {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    var file = BarResources.copy("application--first.bar", folder);
    Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("---------"));
    assumeFalse(Files.isReadable(file), "running as a user that can read everything");

    var error = refused(BarSelection.of(List.of(file.toString()))).error();

    assertThat(error.code()).isEqualTo(CoreErrorCode.IO_ERROR);
    assertThat(error.message()).isEqualTo("The file cannot be read: access is denied");
  }

  @Test
  @DisplayName("ends at the first file that cannot be used: the ones after it are not read")
  void endsAtTheFirstRefusal() {
    var error =
        refused(
                BarSelection.of(
                    List.of(path("not-a-bar.txt"), folder.resolve("missing.bar").toString())))
            .error();

    assertThat(error.details()).containsEntry("param", "files[0]");
  }
}
