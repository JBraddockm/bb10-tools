package com.jbraddockm.link.pairing.store;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link FilesExistException}. */
@DisplayName("FilesExistException: a folder already holds what was to be made")
class FilesExistExceptionTest {

  private static final Path FOLDER = Path.of("devices", "2A3B4C5D");

  private final FilesExistException exception =
      new FilesExistException(
          "a pairing with this device",
          FOLDER,
          List.of(FOLDER.resolve("authority_cert.pem"), FOLDER.resolve("pairing.json")));

  @Test
  @DisplayName("says what is there, where, which files, and what can be done about it")
  void saysWhatIsThere() {
    assertThat(exception.getMessage())
        .isEqualTo(
            "a pairing with this device is in devices/2A3B4C5D already"
                + " ([authority_cert.pem, pairing.json]); replace it, or use another folder");
  }

  @Test
  @DisplayName("names the files by their names and not by their paths")
  void namesTheFilesByName() {
    assertThat(exception.getMessage()).doesNotContain("devices/2A3B4C5D/authority_cert.pem");
  }

  @Test
  @DisplayName("gives the folder as text")
  void givesTheFolder() {
    assertThat(exception.directory()).isEqualTo(FOLDER.toString());
  }

  @Test
  @DisplayName("is not a checked exception")
  void isUnchecked() {
    assertThat(exception).isInstanceOf(RuntimeException.class);
  }
}
