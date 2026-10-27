package com.jbraddockm.link.endpoint.pairing.store;

import java.nio.file.Path;
import java.util.List;

/** A folder already holds what is to be made there, and it may not be replaced. */
public final class FilesExistException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  private final String directory;

  /**
   * Creates the exception.
   *
   * @param what what is there, in words: "a pairing with this device"
   * @param directory the folder
   * @param files the files that are there
   */
  public FilesExistException(String what, Path directory, List<Path> files) {
    super(
        what
            + " is in "
            + directory
            + " already ("
            + files.stream().map(file -> file.getFileName().toString()).toList()
            + "); replace it, or use another folder");
    this.directory = directory.toString();
  }

  /**
   * The folder.
   *
   * @return its path
   */
  public String directory() {
    return directory;
  }
}
