package com.jbraddockm.core.bar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blackberry.tools.bar.PackageType;
import com.jbraddockm.core.testing.Resources;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Tests for {@link BarInspector}: manifest reading, package kind detection, validation, and
 * rejection of unusable files.
 */
@DisplayName("BarInspector: looking at a bar")
class BarInspectorTest {

  @TempDir private Path folder;

  private BarInfo inspect(String manifest) throws Exception {
    return BarInspector.inspect(BarFiles.withManifest(folder, "bar.bar", manifest));
  }

  private BarValidation validate(String manifest) throws Exception {
    return BarInspector.validate(BarFiles.withManifest(folder, "bar.bar", manifest));
  }

  @Nested
  @DisplayName("a real manifest")
  class Real {

    @Test
    @DisplayName(
        "says the kind, the package type, the identifier, the name, the version, the architecture"
            + " and the version of the manifest, and that it is a release build and not signed")
    void saysWhatTheManifestSays() throws Exception {
      var file = BarFiles.withManifest(folder, "real.bar", "real-app-manifest.mf");

      var info = BarInspector.inspect(file);

      assertThat(info.kind()).isEqualTo(BarKind.APPLICATION);
      assertThat(info.packageType()).isEqualTo("application");
      assertThat(info.packageId()).isEqualTo("testRel_le_TLSDeBugc09806e9");
      assertThat(info.packageName()).isEqualTo("com.example.TLSDeBug");
      assertThat(info.packageVersion()).isEqualTo("1.0.0.1");
      assertThat(info.architecture()).isEqualTo("armle-v7");
      assertThat(info.manifestVersion()).isEqualTo("1.5");
      assertThat(info.developmentMode()).isFalse();
      assertThat(info.signed()).isFalse();
      assertThat(info.sizeBytes()).as("the size of the file").isEqualTo(Files.size(file));
      assertThat(info.file()).isEqualTo(file);
    }

    @Test
    @DisplayName("built for development, says so, and nothing else about it changes")
    void builtForDevelopment() throws Exception {
      var release = inspect("real-app-manifest.mf");
      var development = inspect("manifest--real-development.mf");

      assertThat(development.developmentMode()).isTrue();
      assertThat(development.packageId()).isEqualTo(release.packageId());
      assertThat(development.packageVersion()).isEqualTo(release.packageVersion());
    }

    @Test
    @DisplayName(
        "with what signing adds is noticed as signed, and is checked by the rules for a signed"
            + " package")
    void signed() throws Exception {
      assertThat(inspect("manifest--real-signed.mf").signed()).isTrue();
      assertThat(validate("manifest--real-signed.mf").rules()).isEqualTo("signed");
    }

    @Test
    @DisplayName("is written on the wire with its fields in this order, and the kind as its word")
    void onTheWire() throws Exception {
      var json = inspect("real-app-manifest.mf").toJson();

      assertThat(List.copyOf(json.keySet()))
          .containsExactlyElementsOf(
              Resources.text(BarInspectorTest.class, "bar--wire-fields.txt").lines().toList());
      assertThat(json).containsEntry("kind", "application");
    }

    @Test
    @DisplayName(
        "is valid by the rules for an unsigned package, with no finding, and is valid on the wire")
    void isValid() throws Exception {
      var validation = validate("real-app-manifest.mf");

      assertThat(validation.rules()).isEqualTo("unsigned");
      assertThat(validation.valid()).as("valid as unsigned: " + validation.messages()).isTrue();
      assertThat(validation.messages()).isEmpty();
      assertThat(validation.toJson()).containsEntry("valid", true);
    }

    @Test
    @DisplayName(
        "a manifest that breaks the rules is not valid, and says why, and is not valid on the wire")
    void aManifestThatBreaksTheRules() throws Exception {
      var validation = validate("manifest--incomplete.mf");

      assertThat(validation.valid()).isFalse();
      assertThat(validation.messages()).isNotEmpty();
      assertThat(validation.toJson()).containsEntry("valid", false);
    }

    @Test
    @EnabledIfSystemProperty(named = "bb.bar.sample", matches = ".+")
    @DisplayName(
        "a whole real bar, whose path is given in bb.bar.sample, tells what its manifest alone"
            + " tells")
    void aWholeRealBar() throws Exception {
      var sample = Path.of(System.getProperty("bb.bar.sample"));
      var whole = BarInspector.inspect(sample);
      byte[] manifest;
      try (var archive = new ZipFile(sample.toFile())) {
        manifest = archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF")).readAllBytes();
      }
      var alone =
          BarInspector.inspect(
              BarFiles.withEntries(
                  folder, "manifest-only.bar", Map.of("META-INF/MANIFEST.MF", manifest)));

      assertThat(whole.sizeBytes()).isEqualTo(Files.size(sample));
      assertThat(whole.packageId()).isNotNull();
      assertThat(whole.packageVersion()).isNotNull();
      assertThat(alone.kind()).isEqualTo(whole.kind());
      assertThat(alone.packageId()).isEqualTo(whole.packageId());
      assertThat(alone.packageVersion()).isEqualTo(whole.packageVersion());
      assertThat(alone.developmentMode()).isEqualTo(whole.developmentMode());
      assertThat(alone.signed()).isEqualTo(whole.signed());
    }
  }

  @Nested
  @DisplayName("the kind of a package")
  class Kinds {

    @ParameterizedTest(name = "{0} in {1} is {2}")
    @CsvFileSource(
        resources = "/com/jbraddockm/core/bar/bar--kinds.csv",
        delimiter = '|',
        quoteCharacter = '`')
    @DisplayName(
        "is read from the type of the package, from the system type and from the name of the file,"
            + " for a firmware image, whose name may say that it is the radio")
    void isReadFromTheManifestAndTheName(String manifest, String fileName, String kind)
        throws Exception {
      var file = BarFiles.withManifest(folder, fileName, manifest);

      assertThat(BarInspector.inspect(file).kind().wireName()).isEqualTo(kind);
    }

    @Test
    @DisplayName("the type of the package is kept as the manifest says it")
    void thePackageTypeIsKept() throws Exception {
      assertThat(inspect("manifest--debug-token.mf").packageType()).isEqualTo("debug-token");
    }

    @ParameterizedTest(name = "{0}, patch of {1}, system {2}, {3}: {4}")
    @CsvSource({
      "APPLICATION, , , , APPLICATION",
      "APPLICATION, , radio, radio.bar, APPLICATION",
      "SYSTEM, , , os.bar, OPERATING_SYSTEM",
      "SYSTEM, , radio, os.bar, RADIO",
      "SYSTEM, , RADIO, os.bar, RADIO",
      "SYSTEM, , , MyRadio.bar, RADIO",
      "PATCH, system, , p.bar, OPERATING_SYSTEM",
      "PATCH, SYSTEM, , p.bar, OPERATING_SYSTEM",
      "PATCH, system, radio, p.bar, RADIO",
      "PATCH, application, , p.bar, OTHER",
      "PATCH, , , p.bar, OTHER",
      "DEBUG_TOKEN, , , d.bar, OTHER",
      "DEVICE_CONFIG, , , d.bar, OTHER",
      "SYSTEM_DATA, , , d.bar, OTHER"
    })
    @DisplayName(
        "classify gives the kind for each type, whatever the case of the words, and an application"
            + " is never firmware")
    void classify(
        PackageType type,
        String patchPackageType,
        String systemType,
        String fileName,
        BarKind expected) {
      assertThat(
              BarInspector.classify(
                  type, patchPackageType, systemType, fileName == null ? "" : fileName))
          .isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("what the library tolerates")
  class Tolerating {

    @Test
    @DisplayName(
        "a value that continues on the next line, Windows line endings and a byte-order mark are"
            + " read, and so is the attribute after them")
    void anAwkwardManifest() throws Exception {
      var info = inspect("manifest--awkward.mf");

      assertThat(info.packageName()).isEqualTo("a long name that continues on the next line");
      assertThat(info.packageId()).isEqualTo("id1");
    }

    @Test
    @DisplayName("an attribute that is not there is null, and one that is blank is not there")
    void absentAndBlankAttributes() throws Exception {
      assertThat(inspect("manifest--awkward.mf").architecture()).isNull();
      assertThat(inspect("manifest--blank-id.mf").packageId()).isNull();
    }

    @Test
    @DisplayName("the name of the entry of the manifest is matched without regard to case")
    void theEntryIsMatchedWithoutRegardToCase() throws Exception {
      var file =
          BarFiles.withEntries(
              folder,
              "lower.bar",
              Map.of(
                  "meta-inf/manifest.mf",
                  Resources.bytes(BarInspectorTest.class, "manifest--application.mf")));

      assertThat(BarInspector.inspect(file).packageType()).isEqualTo("application");
    }
  }

  @Nested
  @DisplayName("what is refused")
  class Refusing {

    @ParameterizedTest(name = "{0}")
    @CsvFileSource(
        resources = "/com/jbraddockm/core/bar/bar--refusals.csv",
        delimiter = '|',
        quoteCharacter = '`')
    @DisplayName(
        "a manifest with no package type that is known: the words say which, or that there is none")
    void aPackageTypeThatIsNotKnown(String manifest, String reason) throws Exception {
      var file = BarFiles.withManifest(folder, "bad.bar", manifest);

      assertThatThrownBy(() -> BarInspector.inspect(file))
          .isInstanceOf(InvalidBarException.class)
          .hasMessage(reason);
    }

    @Test
    @DisplayName("a file that is not an archive, and one that is empty, are not a ZIP archive")
    void notAnArchive() throws Exception {
      var text =
          Files.write(
              folder.resolve("text.bar"),
              Resources.bytes(BarInspectorTest.class, "bar--not-a-zip.txt"));
      var empty = Files.write(folder.resolve("empty.bar"), new byte[0]);

      assertThatThrownBy(() -> BarInspector.inspect(text))
          .isInstanceOf(InvalidBarException.class)
          .hasMessageStartingWith("it is not a ZIP archive (");
      assertThatThrownBy(() -> BarInspector.inspect(empty))
          .isInstanceOf(InvalidBarException.class)
          .hasMessageContaining("ZIP");
    }

    @Test
    @DisplayName("an archive with no manifest")
    void noManifest() throws Exception {
      var file = BarFiles.withEntries(folder, "n.bar", Map.of("native/x", new byte[] {1}));

      assertThatThrownBy(() -> BarInspector.inspect(file))
          .isInstanceOf(InvalidBarException.class)
          .hasMessage("it has no META-INF/MANIFEST.MF");
    }

    @Test
    @DisplayName("a manifest that cannot be read, with the words of the library")
    void aManifestThatCannotBeRead() throws Exception {
      var file = BarFiles.withManifest(folder, "g.bar", "manifest--garbage.mf");

      assertThatThrownBy(() -> BarInspector.inspect(file))
          .isInstanceOf(InvalidBarException.class)
          .hasMessageStartingWith("its manifest cannot be read: ");
    }

    @Test
    @DisplayName(
        "a manifest that is larger than 8 MiB is refused without being read, so that a hostile file"
            + " cannot fill the memory")
    void aManifestThatIsTooLarge() throws Exception {
      var padding = new byte[BarInspector.MAXIMUM_MANIFEST_BYTES + 1];
      Arrays.fill(padding, (byte) 'x');
      var file = BarFiles.withEntries(folder, "huge.bar", Map.of("META-INF/MANIFEST.MF", padding));

      assertThatThrownBy(() -> BarInspector.inspect(file))
          .isInstanceOf(InvalidBarException.class)
          .hasMessage("its manifest is larger than 8388608 bytes");
    }

    @Test
    @DisplayName("a folder is not a bar, whether it is inspected or validated")
    void aFolder() {
      assertThatThrownBy(() -> BarInspector.inspect(folder))
          .isInstanceOf(InvalidBarException.class)
          .hasMessage("it is a folder, not a file");
      assertThatThrownBy(() -> BarInspector.validate(folder))
          .isInstanceOf(InvalidBarException.class)
          .hasMessage("it is a folder, not a file");
    }

    @Test
    @DisplayName(
        "a file that is not there is an error of reading, whether it is inspected or validated")
    void aFileThatIsNotThere() {
      var missing = folder.resolve("missing.bar");

      assertThatThrownBy(() -> BarInspector.inspect(missing))
          .isInstanceOf(NoSuchFileException.class);
      assertThatThrownBy(() -> BarInspector.validate(missing))
          .isInstanceOf(NoSuchFileException.class);
    }

    @Test
    @DisplayName("validating a file that is not an archive fails the same way")
    void validatingAFileThatIsNotAnArchive() throws Exception {
      var text =
          Files.write(
              folder.resolve("text.bar"),
              Resources.bytes(BarInspectorTest.class, "bar--not-a-zip.txt"));

      assertThatThrownBy(() -> BarInspector.validate(text))
          .isInstanceOf(InvalidBarException.class)
          .hasMessageStartingWith("it is not a ZIP archive (");
    }
  }

  @Nested
  @DisplayName("what is said of a manifest that the rules cannot be applied to")
  class Rejected {

    @Test
    @DisplayName(
        "a manifest with no version of the format is rejected, as an error, with its words")
    void noVersion() throws Exception {
      var validation = validate("manifest--without-version.mf");

      assertThat(validation.valid()).isFalse();
      assertThat(validation.messages())
          .extracting(BarValidation.Message::text)
          .containsExactly("The manifest has no Archive-Manifest-Version.");
    }

    @Test
    @DisplayName(
        "a version of the format that the library does not support is rejected, saying which")
    void unsupportedVersion() throws Exception {
      var validation = validate("manifest--unsupported-version.mf");

      assertThat(validation.valid()).isFalse();
      assertThat(validation.messages())
          .extracting(BarValidation.Message::text)
          .containsExactly("The manifest format version 9.9 is not supported.");
    }
  }
}
