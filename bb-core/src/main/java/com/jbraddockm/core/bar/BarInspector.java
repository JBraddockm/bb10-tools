package com.jbraddockm.core.bar;

import com.blackberry.tools.bar.BarLibrary;
import com.blackberry.tools.bar.IManifest;
import com.blackberry.tools.bar.IResultMessage;
import com.blackberry.tools.bar.PackageType;
import com.blackberry.tools.bar.ParseException;
import com.blackberry.tools.bar.SigningType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Reads the manifest of a bar with BlackBerry's library.
 *
 * <p>Only {@code META-INF/MANIFEST.MF} is read from the archive, and at most {@link
 * #MAXIMUM_MANIFEST_BYTES} of it, so the size of the bar does not matter and a broken or hostile
 * archive cannot exhaust memory. The package's attributes are in a section of their own, not at the
 * top of the manifest; the library looks for an attribute in every section.
 *
 * <p>This is the only class that uses the library. Reading needs nothing from the class path beyond
 * the library's classes. {@link #validate} also loads the rule sets, which are XML resources of the
 * library.
 */
public final class BarInspector {

  /** The largest manifest that is read; a real one of 200 assets is about 40 KB. */
  public static final int MAXIMUM_MANIFEST_BYTES = 8 * 1024 * 1024;

  private static final String MANIFEST_ENTRY = "META-INF/MANIFEST.MF";
  private static final String SIGNED_MARKER = "Package-Author-Certificate-Hash";

  private BarInspector() {}

  /**
   * Reads what the manifest of a bar says about its package.
   *
   * @param file the bar
   * @return the description
   * @throws IOException if the file cannot be read, for example because it does not exist
   * @throws InvalidBarException if the file is not a ZIP archive, has no usable manifest, or names
   *     a package type that is not known
   */
  public static BarInfo inspect(Path file) throws IOException, InvalidBarException {
    var size = sizeOf(file);
    try (var archive = open(file)) {
      var manifest = readManifest(archive);
      var type = manifest.getPackageType();
      if (type == null) {
        var text = manifest.getAttributeValue("Package-Type");
        throw new InvalidBarException(
            text == null || text.isBlank()
                ? "its manifest has no Package-Type"
                : "its Package-Type '" + text + "' is not a package type that is known");
      }
      var fileName = file.getFileName() == null ? "" : file.getFileName().toString();
      return new BarInfo(
          file,
          size,
          classify(
              type,
              manifest.getAttributeValue("Patch-Package-Type"),
              manifest.getAttributeValue("System-Type"),
              fileName),
          type.toString(),
          value(manifest, "Package-Id"),
          value(manifest, "Package-Name"),
          value(manifest, "Package-Version"),
          value(manifest, "Package-Architecture"),
          manifest.getVersion() == null ? null : manifest.getVersion().toString(),
          "true".equalsIgnoreCase(value(manifest, "Application-Development-Mode")),
          isSigned(manifest));
    }
  }

  /**
   * Checks the manifest of a bar against BlackBerry's rules for its kind of package.
   *
   * <p>The rules for a signed package are used if the manifest carries the attribute that signing
   * adds, and those for an unsigned package otherwise. The signature is not checked.
   *
   * @param file the bar
   * @return what the rules found; not valid if they could not be applied
   * @throws IOException if the file cannot be read
   * @throws InvalidBarException if the file is not a ZIP archive or has no usable manifest
   */
  public static BarValidation validate(Path file) throws IOException, InvalidBarException {
    sizeOf(file);
    try (var archive = open(file)) {
      return validate(readManifest(archive));
    }
  }

  /**
   * Decides what a bar contains from its package type.
   *
   * <p>Firmware is a package of type {@code system}, or a patch whose {@code Patch-Package-Type} is
   * {@code system}. It is the radio if {@code System-Type} says {@code radio} or the name of the
   * file contains {@code radio}, and the operating system otherwise. The rule about the file name
   * is the research's; it is applied to firmware only.
   */
  static BarKind classify(
      PackageType type, String patchPackageType, String systemType, String fileName) {
    return switch (type) {
      case APPLICATION -> BarKind.APPLICATION;
      case SYSTEM -> firmware(systemType, fileName);
      case PATCH ->
          "system".equalsIgnoreCase(patchPackageType)
              ? firmware(systemType, fileName)
              : BarKind.OTHER;
      case DEBUG_TOKEN, DEVICE_CONFIG, SYSTEM_DATA -> BarKind.OTHER;
    };
  }

  private static BarKind firmware(String systemType, String fileName) {
    var radio =
        "radio".equalsIgnoreCase(systemType) || fileName.toLowerCase(Locale.ROOT).contains("radio");
    return radio ? BarKind.RADIO : BarKind.OPERATING_SYSTEM;
  }

  private static long sizeOf(Path file) throws IOException, InvalidBarException {
    if (Files.isDirectory(file)) {
      throw new InvalidBarException("it is a folder, not a file");
    }
    return Files.size(file);
  }

  private static ZipFile open(Path file) throws IOException, InvalidBarException {
    try {
      return new ZipFile(file.toFile());
    } catch (ZipException failure) {
      throw new InvalidBarException("it is not a ZIP archive (" + failure.getMessage() + ")");
    }
  }

  private static IManifest readManifest(ZipFile archive) throws IOException, InvalidBarException {
    var entry = archive.getEntry(MANIFEST_ENTRY);
    if (entry == null) {
      entry =
          archive.stream()
              .filter(candidate -> candidate.getName().equalsIgnoreCase(MANIFEST_ENTRY))
              .findFirst()
              .orElseThrow(() -> new InvalidBarException("it has no " + MANIFEST_ENTRY));
    }
    if (entry.getSize() > MAXIMUM_MANIFEST_BYTES) {
      throw new InvalidBarException(
          "its manifest is larger than " + MAXIMUM_MANIFEST_BYTES + " bytes");
    }
    byte[] bytes;
    try (var stream = archive.getInputStream(entry)) {
      bytes = stream.readNBytes(MAXIMUM_MANIFEST_BYTES + 1);
    }
    if (bytes.length > MAXIMUM_MANIFEST_BYTES) {
      throw new InvalidBarException(
          "its manifest is larger than " + MAXIMUM_MANIFEST_BYTES + " bytes");
    }
    try {
      return BarLibrary.createManifest(new ByteArrayInputStream(bytes));
    } catch (ParseException | IllegalArgumentException failure) {
      throw new InvalidBarException(
          "its manifest cannot be read: " + withoutMarker(failure.getMessage()));
    }
  }

  private static BarValidation validate(IManifest manifest) {
    var signed = isSigned(manifest);
    var rules = signed ? "signed" : "unsigned";
    var version = manifest.getVersion();
    var type = manifest.getPackageType();
    if (version == null) {
      return rejected(rules, "The manifest has no Archive-Manifest-Version.");
    }
    if (type == null) {
      return rejected(rules, "The manifest has no Package-Type that is known.");
    }
    try {
      if (!BarLibrary.isSpecVersionSupported(version)) {
        return rejected(rules, "The manifest format version " + version + " is not supported.");
      }
      var result =
          manifest.validate(
              BarLibrary.getRuleSet(
                  version, type, signed ? SigningType.SIGNED : SigningType.UNSIGNED));
      var messages = new ArrayList<BarValidation.Message>();
      for (IResultMessage found : result.getMessages()) {
        messages.add(
            new BarValidation.Message(
                found.isError() ? BarValidation.Severity.ERROR : BarValidation.Severity.WARNING,
                found.getMessageString()));
      }
      return new BarValidation(rules, messages);
    } catch (RuntimeException failure) {
      // The rules are XML resources of the library; if they cannot be loaded or applied, that is
      // reported as a finding and does not take the caller down.
      return rejected(rules, "The rules could not be applied: " + failure);
    }
  }

  private static BarValidation rejected(String rules, String text) {
    return new BarValidation(
        rules, List.of(new BarValidation.Message(BarValidation.Severity.ERROR, text)));
  }

  /** Signing adds the hash of the author's certificate to the manifest. */
  private static boolean isSigned(IManifest manifest) {
    return value(manifest, SIGNED_MARKER) != null;
  }

  private static String value(IManifest manifest, String name) {
    var text = manifest.getAttributeValue(name);
    return text == null || text.isBlank() ? null : text.strip();
  }

  private static String withoutMarker(String message) {
    if (message == null) {
      return "unknown error";
    }
    return message.startsWith("[ERROR] ") ? message.substring("[ERROR] ".length()) : message;
  }
}
