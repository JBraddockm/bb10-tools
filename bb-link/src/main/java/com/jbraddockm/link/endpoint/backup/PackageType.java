package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A package type within the {@code app} category, used to sub-select a rev2 backup: the device's
 * {@code pkgtype} vocabulary. It applies to {@code app} only; media and settings have no package
 * types.
 */
public enum PackageType implements WireValue {

  /** The device's built-in system applications. */
  SYSTEM("system"),

  /** Application data (a single installed app's data). */
  DATA("data"),

  /** Application binaries. */
  BIN("bin");

  private final String wireName;

  PackageType(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Recognises a package type by its wire name, without regard to case.
   *
   * @param text the wire name, such as {@code data}
   * @return the type, or empty if the name is not one the device uses
   */
  static Optional<PackageType> fromWire(String text) {
    var trimmed = text.strip();
    return Arrays.stream(values())
        .filter(type -> type.wireName.equalsIgnoreCase(trimmed))
        .findFirst();
  }

  /**
   * Builds the {@code pkgtype} value for a selection: the types' wire names joined by a comma, in
   * the device's own order and without duplicates.
   *
   * @param types the selected package types
   * @return for example {@code system,data}
   */
  static String csv(List<PackageType> types) {
    return Arrays.stream(values())
        .filter(types::contains)
        .map(PackageType::wireName)
        .collect(Collectors.joining(","));
  }

  /**
   * Turns a list of wire names into package types, in the device's own order and without
   * duplicates.
   *
   * @param names the wire names a caller asked for
   * @return the types, in device order
   * @throws InvalidArgumentException naming {@code packageTypes} if a name is not a known type
   */
  public static List<PackageType> select(List<String> names) {
    var selected = new ArrayList<PackageType>();
    for (var name : names) {
      var type =
          fromWire(name)
              .orElseThrow(
                  () ->
                      new InvalidArgumentException(
                          "packageTypes",
                          "must be one of system, data or bin, not '" + name + "'"));
      if (!selected.contains(type)) {
        selected.add(type);
      }
    }
    return Arrays.stream(values()).filter(selected::contains).toList();
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
