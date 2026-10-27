package com.jbraddockm.link.update;

import java.util.Arrays;
import java.util.Optional;

/** What the {@code Status} of an answer of an update conversation says. */
enum UpdateStatus {

  /** The device is ready. */
  READY("Ready"),

  /** The device is working on it. */
  IN_PROGRESS("InProgress"),

  /** The device has finished. */
  SUCCESS("Success"),

  /** The device has finished; the research lists this next to {@code Success}. */
  COMPLETE("Complete"),

  /** The device could not do it. */
  ERROR("Error");

  private final String wireName;

  UpdateStatus(String wireName) {
    this.wireName = wireName;
  }

  /**
   * Returns the spelling used by the device.
   *
   * @return for example {@code InProgress}
   */
  public String wireName() {
    return wireName;
  }

  /**
   * Recognises a status, without regard to case.
   *
   * @param text the content of {@code Status}
   * @return the status, or empty if the device used a word that is not known
   */
  public static Optional<UpdateStatus> of(String text) {
    var trimmed = text.strip();
    return Arrays.stream(values())
        .filter(status -> status.wireName.equalsIgnoreCase(trimmed))
        .findFirst();
  }
}
