package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.link.transport.xml.MalformedXmlException;
import com.jbraddockm.link.transport.xml.XmlElement;
import com.jbraddockm.link.transport.xml.XmlParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The manifest of a {@code .bbb} archive. This is bb-link's own, simple format — it is not meant to
 * interchange with BlackBerry Link, only to describe what bb-link itself must upload to restore: the
 * categories, the archive entry that holds each, and its byte count. The archive bytes are the
 * device's own encrypted output, kept verbatim, so the manifest records no encryption.
 *
 * @param pin the device PIN the backup was taken from, or {@code null} if it was not recorded
 * @param softwareVersion the device software version, or {@code null} if it was not recorded
 * @param archives the categories in the archive, in order
 */
record BackupManifest(String pin, String softwareVersion, List<Entry> archives) {

  /** The root element and the version this writer produces. */
  private static final String ROOT = "bbLinkBackup";

  private static final String VERSION = "1";

  /** The name of the manifest entry inside a {@code .bbb} archive. */
  static final String ENTRY_NAME = "Manifest.xml";

  /**
   * One category recorded in the manifest.
   *
   * @param category the category
   * @param fileName the entry inside the archive that holds it, such as {@code Archive/app.tar}
   * @param bytesize the number of bytes of that entry
   */
  record Entry(BackupCategory category, String fileName, long bytesize) {

    /** Validates the entry. */
    Entry {
      Objects.requireNonNull(category, "category");
      Objects.requireNonNull(fileName, "fileName");
    }
  }

  /** Takes an immutable copy of the entries. */
  BackupManifest {
    archives = List.copyOf(archives);
  }

  /**
   * Builds a manifest from downloaded categories.
   *
   * @param pin the device PIN, or {@code null}
   * @param softwareVersion the device software version, or {@code null}
   * @param categories the categories that were downloaded
   * @return the manifest
   */
  static BackupManifest of(
      String pin, String softwareVersion, List<DownloadedCategory> categories) {
    var entries =
        categories.stream()
            .map(
                downloaded ->
                    new Entry(
                        downloaded.category(),
                        downloaded.category().tarEntryName(),
                        downloaded.bytes()))
            .toList();
    return new BackupManifest(pin, softwareVersion, entries);
  }

  /**
   * Serialises the manifest to XML.
   *
   * @return the manifest XML
   */
  String toXml() {
    var builder = new StringBuilder();
    builder.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    builder.append('<').append(ROOT).append(" version=\"").append(VERSION).append("\">\n");
    builder.append("  <SourceDevice");
    attribute(builder, "pin", pin);
    attribute(builder, "softwareVersion", softwareVersion);
    builder.append("/>\n");
    builder.append("  <Archives>\n");
    for (var entry : archives) {
      builder.append("    <Archive");
      attribute(builder, "category", entry.category().wireName());
      attribute(builder, "file", entry.fileName());
      attribute(builder, "bytesize", Long.toString(entry.bytesize()));
      builder.append("/>\n");
    }
    builder.append("  </Archives>\n");
    builder.append("</").append(ROOT).append(">\n");
    return builder.toString();
  }

  /**
   * Parses a manifest from XML.
   *
   * @param xml the manifest XML
   * @return the manifest
   * @throws IllegalArgumentException if the XML is not a well-formed manifest
   */
  static BackupManifest parse(String xml) {
    XmlElement root;
    try {
      root = XmlParser.parse(xml);
    } catch (MalformedXmlException malformed) {
      throw new IllegalArgumentException("the manifest is not well-formed XML", malformed);
    }
    if (!root.name().equals(ROOT)) {
      throw new IllegalArgumentException(
          "the manifest root is " + root.name() + ", not " + ROOT);
    }
    var source = root.child("SourceDevice");
    var pin = source.flatMap(element -> element.attribute("pin")).orElse(null);
    var softwareVersion =
        source.flatMap(element -> element.attribute("softwareVersion")).orElse(null);
    var entries = new ArrayList<Entry>();
    for (var element : root.at("Archives").map(XmlElement::children).orElse(List.of())) {
      if (!element.name().equals("Archive")) {
        continue;
      }
      var category = element.attribute("category").flatMap(BackupCategory::fromWire);
      if (category.isEmpty()) {
        continue;
      }
      var fileName =
          element.attribute("file").orElse(category.get().tarEntryName());
      entries.add(new Entry(category.get(), fileName, longAttribute(element)));
    }
    if (entries.isEmpty()) {
      throw new IllegalArgumentException("the manifest lists no categories");
    }
    return new BackupManifest(pin, softwareVersion, entries);
  }

  private static long longAttribute(XmlElement element) {
    return element
        .attribute("bytesize")
        .map(String::strip)
        .flatMap(
            text -> {
              try {
                return Optional.of(Long.parseLong(text));
              } catch (NumberFormatException _) {
                return Optional.<Long>empty();
              }
            })
        .orElse(0L);
  }

  private static void attribute(StringBuilder builder, String name, String value) {
    if (value != null) {
      builder.append(' ').append(name).append("=\"").append(escape(value)).append('"');
    }
  }

  private static String escape(String value) {
    return value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
