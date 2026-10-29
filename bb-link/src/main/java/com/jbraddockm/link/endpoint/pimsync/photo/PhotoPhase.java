package com.jbraddockm.link.endpoint.pimsync.photo;

import com.jbraddockm.core.command.ProgressPhase;

/** The phases that the progress of a contact-photo transfer names. */
enum PhotoPhase implements ProgressPhase {

  /** Streaming a contact photo from the device to a file. */
  FETCHING("fetching"),

  /** Sending a contact photo to the device as a multipart upload. */
  SENDING("sending");

  private final String wireName;

  PhotoPhase(String wireName) {
    this.wireName = wireName;
  }

  @Override
  public String wireName() {
    return wireName;
  }
}
