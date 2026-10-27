package com.jbraddockm.link.transport.cgi;

/** Told how many bytes of a file have been sent to the device. */
@FunctionalInterface
public interface UploadObserver {

  /** An observer that does nothing. */
  UploadObserver NONE = _ -> {};

  /**
   * Called while a file is sent, with the total so far.
   *
   * @param bytes the number of bytes sent
   */
  void sent(long bytes);
}
