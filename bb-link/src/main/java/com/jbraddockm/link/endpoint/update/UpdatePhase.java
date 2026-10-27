package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.core.command.ProgressPhase;

/** The phases that the progress of installing and removing packages names. */
enum UpdatePhase implements ProgressPhase {

  /** Looking at the device before anything is sent. */
  CHECKING("checking"),

  /** Sending a package to the device; the progress has the bytes. */
  UPLOADING("uploading"),

  /** The device installing a package; the progress is the device's own percentage. */
  INSTALLING("installing"),

  /** The device removing a package. */
  REMOVING("removing");

  private final String wireName;

  UpdatePhase(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
