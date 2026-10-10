package com.jbraddockm;

import java.io.IOException;

/**
 * Entry point under the name the build configuration already uses, so that nothing there has to
 * change. Everything happens in {@link Launcher}.
 */
public final class Deploy {

  private Deploy() {}

  /**
   * Starts the application.
   *
   * @param arguments the command-line arguments
   * @throws IOException if reading the daemon's input fails
   */
  static void main(String[] arguments) throws IOException {
    Launcher.main(arguments);
  }
}
