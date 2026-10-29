package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.WireValue;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A category of data a backup covers. The device names three: applications, media and settings. A
 * backup selects any subset of them; the device is told the selection as the {@code mode} field,
 * the categories joined by an underscore in the device's own order.
 */
public enum BackupCategory implements WireValue {

  /** Installed applications and their data. */
  APP("app"),

  /** Pictures, music and other media files. */
  MEDIA("media"),

  /** Device settings, accounts and personal information. */
  SETTINGS("settings");

  private final String wireName;

  BackupCategory(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }

  /**
   * Returns the name of the entry that holds this category inside a {@code .bbb} archive.
   *
   * @return for example {@code Archive/settings.tar}
   */
  String tarEntryName() {
    return "Archive/" + wireName + ".tar";
  }

  /**
   * Recognises a category by its wire name, without regard to case.
   *
   * @param text the wire name, such as {@code settings}
   * @return the category, or empty if the name is not one the device uses
   */
  static Optional<BackupCategory> fromWire(String text) {
    var trimmed = text.strip();
    return Arrays.stream(values())
        .filter(category -> category.wireName.equalsIgnoreCase(trimmed))
        .findFirst();
  }

  /**
   * Builds the {@code mode} value for a selection: the categories' wire names joined by an
   * underscore, in the device's own order regardless of how they were given.
   *
   * @param categories the selected categories
   * @return for example {@code app_media_settings}
   */
  static String modeValue(List<BackupCategory> categories) {
    return Arrays.stream(values())
        .filter(categories::contains)
        .map(BackupCategory::wireName)
        .collect(Collectors.joining("_"));
  }

  /**
   * Turns a list of wire names into categories, in the device's own order and without duplicates.
   *
   * @param names the wire names a caller asked for
   * @return the categories, in device order
   * @throws InvalidArgumentException naming {@code categories} if a name is not a known category
   */
  public static List<BackupCategory> select(List<String> names) {
    var selected = new ArrayList<BackupCategory>();
    for (var name : names) {
      var category =
          fromWire(name)
              .orElseThrow(
                  () ->
                      new InvalidArgumentException(
                          "categories", "must be one of app, media or settings, not '" + name + "'"));
      if (!selected.contains(category)) {
        selected.add(category);
      }
    }
    return Arrays.stream(values()).filter(selected::contains).toList();
  }
}
