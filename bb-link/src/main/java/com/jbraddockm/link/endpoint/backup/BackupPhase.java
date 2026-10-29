package com.jbraddockm.link.endpoint.backup;

import com.jbraddockm.core.command.ProgressPhase;

/** The phases that the progress of a backup or restore names. */
enum BackupPhase implements ProgressPhase {

  /** Reading the list of categories the device can back up. */
  LISTING("listing"),

  /** The device preparing an archive before it can be downloaded. */
  PREPARING("preparing"),

  /** Downloading a category's archive from the device; the progress has the bytes. */
  DOWNLOADING("downloading"),

  /** Writing the downloaded categories into a {@code .bbb} file. */
  PACKAGING("packaging"),

  /** Uploading a category's archive to the device; the progress has the bytes. */
  UPLOADING("uploading"),

  /** Finishing the conversation after the data has moved. */
  FINISHING("finishing");

  private final String wireName;

  BackupPhase(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
