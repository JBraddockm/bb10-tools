package com.jbraddockm.link.testing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Resources}: lookup in the folder of the package, and substitution of
 * placeholders.
 */
@DisplayName("Resources: the data of the tests, in files that mirror the packages")
class ResourcesTest {

  private static final Map<String, String> BOTH = Map.of("name", "Ada", "role", "an engineer");

  @Test
  @DisplayName("text gives a file as it is, placeholders and all")
  void textGivesTheFileAsItIs() {
    assertThat(Resources.text(ResourcesTest.class, "greeting.txt"))
        .contains("{{name}}")
        .contains("{{role}}");
  }

  @Test
  @DisplayName("text with values fills each placeholder with its value")
  void textFillsThePlaceholders() {
    assertThat(Resources.text(ResourcesTest.class, "greeting.txt", BOTH))
        .isEqualTo(Resources.text(ResourcesTest.class, "greeting--filled.txt"));
  }

  @Test
  @DisplayName("text with values writes a value that has $ or backslashes as it is")
  void textKeepsSpecialCharactersOfAValue() {
    var values = Map.of("name", "a$1b", "role", "c\\d");

    assertThat(Resources.text(ResourcesTest.class, "greeting.txt", values))
        .contains("a$1b")
        .contains("c\\d");
  }

  @Test
  @DisplayName("text with values fails if a placeholder has no value, and names it")
  void textFailsForAPlaceholderWithoutValue() {
    assertThatIllegalStateException()
        .isThrownBy(
            () ->
                Resources.text(
                    ResourcesTest.class, "greeting--two-names.txt", Map.of("name", "Ada")))
        .withMessageContaining("{{other}}");
  }

  @Test
  @DisplayName("text with values fails if a value has no placeholder, and names it")
  void textFailsForAValueWithoutPlaceholder() {
    var values = Map.of("name", "Ada", "role", "an engineer", "extra", "x");

    assertThatIllegalArgumentException()
        .isThrownBy(() -> Resources.text(ResourcesTest.class, "greeting.txt", values))
        .withMessageContaining("[extra]");
  }

  @Test
  @DisplayName("a resource that is not there fails, and says where it belongs")
  void aMissingResourceFails() {
    assertThatExceptionOfType(AssertionError.class)
        .isThrownBy(() -> Resources.text(ResourcesTest.class, "not-there.txt"))
        .withMessageContaining("com/jbraddockm/link/testing/not-there.txt")
        .withMessageContaining("src/test/resources");
  }

  @Test
  @DisplayName("bytes gives the same content as text")
  void bytesGivesTheSameContent() {
    assertThat(Resources.bytes(ResourcesTest.class, "greeting--filled.txt"))
        .isEqualTo(Resources.text(ResourcesTest.class, "greeting--filled.txt").getBytes(UTF_8));
  }

  @Test
  @DisplayName("copyTo puts a copy in the folder, under the same name")
  void copyToPutsACopyInTheFolder(@TempDir Path folder) {
    var copy = Resources.copyTo(ResourcesTest.class, "greeting--filled.txt", folder);

    assertThat(copy)
        .hasParent(folder)
        .hasFileName("greeting--filled.txt")
        .hasContent(Resources.text(ResourcesTest.class, "greeting--filled.txt"));
  }
}
