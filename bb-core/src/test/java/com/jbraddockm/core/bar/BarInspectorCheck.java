package com.jbraddockm.core.bar;

import static com.jbraddockm.core.daemon.Checks.check;
import static com.jbraddockm.core.daemon.Checks.equal;

import com.blackberry.tools.bar.PackageType;
import com.jbraddockm.core.daemon.Checks;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Checks the reading of bars with BlackBerry's library: a real manifest, every kind of package, the
 * files that are not bars, and what the library tolerates.
 *
 * <p>The bars are made here, in a temporary folder. The one real manifest is a test resource. A
 * whole real bar can be checked as well by giving its path in the system property {@code
 * bb.bar.sample}.
 */
public final class BarInspectorCheck {

  private BarInspectorCheck() {}

  /** A manifest as a real one starts: an archive section, then the package's own section. */
  private static String manifest(String... packageAttributes) {
    var text = new StringBuilder("Archive-Manifest-Version: 1.5\nArchive-Created-By: a test\n\n");
    for (var attribute : packageAttributes) {
      text.append(attribute).append('\n');
    }
    return text.toString();
  }

  private static Path bar(Path directory, String name, String manifestText) throws IOException {
    return archive(
        directory,
        name,
        Map.of("META-INF/MANIFEST.MF", manifestText.getBytes(StandardCharsets.UTF_8)));
  }

  private static Path archive(Path directory, String name, Map<String, byte[]> entries)
      throws IOException {
    var file = directory.resolve(name);
    try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
      for (var entry : entries.entrySet()) {
        out.putNextEntry(new ZipEntry(entry.getKey()));
        out.write(entry.getValue());
        out.closeEntry();
      }
    }
    return file;
  }

  private static String realManifest() throws IOException {
    try (InputStream stream = BarInspectorCheck.class.getResourceAsStream("real-app-manifest.mf")) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static String kindOf(Path file) {
    try {
      return BarInspector.inspect(file).kind().wireName();
    } catch (IOException | InvalidBarException failure) {
      return "FAILED: " + failure;
    }
  }

  private static String invalidBecause(Path file) {
    try {
      BarInspector.inspect(file);
      return "no exception";
    } catch (InvalidBarException failure) {
      return failure.getMessage();
    } catch (IOException failure) {
      return "I/O: " + failure;
    }
  }

  /**
   * Runs the checks.
   *
   * @param arguments not used
   * @throws Exception if the harness itself fails
   */
  public static void main(String[] arguments) throws Exception {
    var directory = Files.createTempDirectory("bb-core-bar");
    try {
      // a real manifest
      var real =
          archive(
              directory,
              "real.bar",
              Map.of(
                  "META-INF/MANIFEST.MF",
                  realManifest().getBytes(StandardCharsets.UTF_8),
                  "native/TLSDeBug",
                  new byte[] {1, 2, 3}));
      var info = BarInspector.inspect(real);
      equal(BarKind.APPLICATION, info.kind(), "the real manifest: kind");
      equal("application", info.packageType(), "the real manifest: package type");
      equal("testRel_le_TLSDeBugc09806e9", info.packageId(), "the real manifest: id");
      equal("com.example.TLSDeBug", info.packageName(), "the real manifest: name");
      equal("1.0.0.1", info.packageVersion(), "the real manifest: version");
      equal("armle-v7", info.architecture(), "the real manifest: architecture");
      equal("1.5", info.manifestVersion(), "the real manifest: manifest version");
      equal(false, info.developmentMode(), "the real manifest says it is a release build");
      check(
          realManifest().contains("Application-Development-Mode: false"),
          "(the manifest used here says what the check above expects)");
      var development =
          BarInspector.inspect(
              bar(
                  directory,
                  "development.bar",
                  realManifest()
                      .replace(
                          "Application-Development-Mode: false",
                          "Application-Development-Mode: true")));
      equal(true, development.developmentMode(), "the same manifest, built for development mode");
      equal(info.packageId(), development.packageId(), "and nothing else about it changes");
      equal(false, info.signed(), "the real manifest is not signed");
      equal(Files.size(real), info.sizeBytes(), "the size is the size of the file");
      equal(
          List.of(
              "file",
              "sizeBytes",
              "kind",
              "packageType",
              "packageId",
              "packageName",
              "packageVersion",
              "architecture",
              "manifestVersion",
              "developmentMode",
              "signed"),
          List.copyOf(info.toJson().keySet()),
          "the wire representation, in order");
      equal("application", info.toJson().get("kind"), "the kind on the wire");

      var validation = BarInspector.validate(real);
      equal(
          "unsigned", validation.rules(), "an unsigned manifest is checked by the unsigned rules");
      check(validation.valid(), "the real manifest is valid as unsigned: " + validation.messages());
      equal(List.of(), validation.messages(), "and has no finding");
      equal(true, validation.toJson().get("valid"), "valid on the wire");

      // the same manifest, with what signing adds
      var signedText =
          realManifest()
              .replace(
                  "Package-Architecture: armle-v7\n",
                  "Package-Architecture: armle-v7\n"
                      + "Package-Author-Certificate-Hash: abc\n"
                      + "Package-Issue-Date: 1700000000\n");
      var signedInfo = BarInspector.inspect(bar(directory, "signed.bar", signedText));
      equal(true, signedInfo.signed(), "the attribute that signing adds is noticed");
      equal(
          "signed",
          BarInspector.validate(bar(directory, "signed2.bar", signedText)).rules(),
          "and the rules for a signed package are used");
      var unsignedAsSigned =
          BarInspector.validate(
              bar(
                  directory,
                  "u.bar",
                  manifest("Package-Type: application", "Package-Id: x", "Package-Version: 1")));
      check(!unsignedAsSigned.valid(), "a manifest that breaks the rules is not valid");
      check(!unsignedAsSigned.messages().isEmpty(), "and says why");
      equal(false, unsignedAsSigned.toJson().get("valid"), "invalid on the wire");

      // a whole real bar, if one is given
      var sample = System.getProperty("bb.bar.sample");
      if (sample != null) {
        var whole = BarInspector.inspect(Path.of(sample));
        equal(Files.size(Path.of(sample)), whole.sizeBytes(), "the whole real bar: size");
        check(
            whole.packageId() != null && whole.packageVersion() != null,
            "the whole real bar: id and version");
        // reading depends on the manifest and on nothing else of the archive
        try (var archive = new java.util.zip.ZipFile(sample)) {
          var text =
              new String(
                  archive.getInputStream(archive.getEntry("META-INF/MANIFEST.MF")).readAllBytes(),
                  StandardCharsets.UTF_8);
          var manifestOnly = BarInspector.inspect(bar(directory, "manifest-only.bar", text));
          equal(
              manifestOnly.kind(),
              whole.kind(),
              "the whole real bar: the same kind as its manifest alone");
          equal(manifestOnly.packageId(), whole.packageId(), "the whole real bar: the same id");
          equal(
              manifestOnly.packageVersion(),
              whole.packageVersion(),
              "the whole real bar: the same version");
          equal(
              manifestOnly.developmentMode(),
              whole.developmentMode(),
              "the whole real bar: the same mode");
          equal(manifestOnly.signed(), whole.signed(), "the whole real bar: the same signing");
        }
      }

      // every kind of package
      equal(
          "application",
          kindOf(bar(directory, "a.bar", manifest("Package-Type: application"))),
          "application");
      equal(
          "operatingSystem",
          kindOf(bar(directory, "os.bar", manifest("Package-Type: system"))),
          "system is the operating system");
      equal(
          "radio",
          kindOf(bar(directory, "r1.bar", manifest("Package-Type: system", "System-Type: radio"))),
          "System-Type radio");
      equal(
          "radio",
          kindOf(bar(directory, "my-radio-image.bar", manifest("Package-Type: system"))),
          "a firmware file named radio");
      equal(
          "application",
          kindOf(bar(directory, "radioplayer.bar", manifest("Package-Type: application"))),
          "an application is not a radio because of its name");
      equal(
          "operatingSystem",
          kindOf(
              bar(
                  directory,
                  "p1.bar",
                  manifest("Package-Type: patch", "Patch-Package-Type: system"))),
          "a system patch");
      equal(
          "radio",
          kindOf(
              bar(
                  directory,
                  "p2.bar",
                  manifest(
                      "Package-Type: patch", "Patch-Package-Type: system", "System-Type: radio"))),
          "a radio patch");
      equal(
          "other",
          kindOf(bar(directory, "p3.bar", manifest("Package-Type: patch"))),
          "another patch");
      equal(
          "other",
          kindOf(bar(directory, "t.bar", manifest("Package-Type: debug-token"))),
          "a debug token");
      equal(
          "other",
          kindOf(bar(directory, "c.bar", manifest("Package-Type: device-configuration"))),
          "device configuration");
      equal(
          "other",
          kindOf(bar(directory, "d.bar", manifest("Package-Type: system-data"))),
          "system data");
      equal(
          "debug-token",
          BarInspector.inspect(bar(directory, "t2.bar", manifest("Package-Type: debug-token")))
              .packageType(),
          "the package type is kept as the manifest says it");
      equal(
          BarKind.APPLICATION,
          BarInspector.classify(PackageType.APPLICATION, null, null, ""),
          "classify, directly");
      check(
          BarKind.RADIO.isFirmware() && BarKind.OPERATING_SYSTEM.isFirmware(),
          "firmware is firmware");
      check(
          !BarKind.APPLICATION.isFirmware() && !BarKind.OTHER.isFirmware(), "and nothing else is");

      // what the library tolerates
      var awkward =
          "\uFEFFArchive-Manifest-Version: 1.5\r\n"
              + "Archive-Created-By: a test\r\n\r\n"
              + "Package-Type: application\r\n"
              + "Package-Name: a long name that\r\n"
              + "  continues on the next line\r\n"
              + "Package-Id: id1\r\n";
      var tolerated = BarInspector.inspect(bar(directory, "awkward.bar", awkward));
      equal(
          "a long name that continues on the next line",
          tolerated.packageName(),
          "a wrapped value, Windows line endings and a byte-order mark");
      equal("id1", tolerated.packageId(), "and the attribute after it");
      equal(null, tolerated.architecture(), "an absent attribute is null");
      equal(
          "application",
          BarInspector.inspect(
                  archive(
                      directory,
                      "lower.bar",
                      Map.of(
                          "meta-inf/manifest.mf",
                          manifest("Package-Type: application").getBytes(StandardCharsets.UTF_8))))
              .packageType(),
          "the name of the manifest entry is matched without regard to case");
      equal(
          null,
          BarInspector.inspect(
                  bar(
                      directory,
                      "blank.bar",
                      manifest("Package-Type: application", "Package-Id:  ")))
              .packageId(),
          "a blank value is absent");

      // files that are not usable bars
      var notZip = Files.writeString(directory.resolve("text.bar"), "this is not an archive");
      check(
          invalidBecause(notZip).contains("not a ZIP archive"),
          "a file that is not a ZIP: " + invalidBecause(notZip));
      var empty = Files.write(directory.resolve("empty.bar"), new byte[0]);
      check(invalidBecause(empty).contains("ZIP"), "an empty file: " + invalidBecause(empty));
      var noManifest = archive(directory, "nomanifest.bar", Map.of("native/x", new byte[] {1}));
      check(
          invalidBecause(noManifest).contains("no META-INF/MANIFEST.MF"),
          "no manifest: " + invalidBecause(noManifest));
      check(
          invalidBecause(bar(directory, "gizmo.bar", manifest("Package-Type: gizmo")))
              .contains("'gizmo' is not a package type that is known"),
          "an unknown package type");
      check(
          invalidBecause(bar(directory, "notype.bar", manifest("Package-Id: x")))
              .contains("no Package-Type"),
          "no package type");
      check(
          invalidBecause(bar(directory, "garbage.bar", "this is not a manifest at all"))
              .contains("cannot be read"),
          "a manifest that cannot be read");
      check(
          invalidBecause(bar(directory, "blank-manifest.bar", "\n")).contains("no Package-Type"),
          "an empty manifest has no package type");
      var padding =
          "Package-Type: application\n"
              + "Padding: "
              + "x".repeat(BarInspector.MAXIMUM_MANIFEST_BYTES)
              + "\n";
      check(
          invalidBecause(bar(directory, "huge.bar", padding)).contains("larger than"),
          "a manifest that is too large");
      check(invalidBecause(directory).contains("folder"), "a folder");
      try {
        BarInspector.inspect(directory.resolve("missing.bar"));
        check(false, "a file that is not there should be an I/O error");
      } catch (NoSuchFileException expected) {
        check(true, "a file that is not there is an I/O error");
      }
      try {
        BarInspector.validate(notZip);
        check(false, "validating a file that is not a ZIP should fail");
      } catch (InvalidBarException expected) {
        check(true, "validating a file that is not a ZIP fails");
      }

      // the records
      var onlyWarning =
          new BarValidation(
              "unsigned",
              List.of(new BarValidation.Message(BarValidation.Severity.WARNING, "hmm")));
      check(onlyWarning.valid(), "a warning does not make a manifest invalid");
      var listed = (List<?>) onlyWarning.toJson().get("messages");
      equal(1, listed.size(), "a warning is listed");
      equal(
          Map.of("severity", "warning", "text", "hmm"),
          Map.copyOf((Map<?, ?>) listed.getFirst()),
          "with its severity and text");
    } finally {
      try (Stream<Path> files = Files.walk(directory)) {
        files
            .sorted(java.util.Comparator.reverseOrder())
            .forEach(
                file -> {
                  try {
                    Files.delete(file);
                  } catch (IOException failure) {
                    throw new UncheckedIOException(failure);
                  }
                });
      }
    }
    System.exit(Checks.finish("bb-core bar"));
  }
}
