package com.qnx.bbt.deploy.ui;

/**
 * Shadow implementation of the original {@code Gui} class.
 *
 * <p>This class is provided to satisfy references to the BlackBerry tool's {@code Gui} class while
 * building a headless native executable with GraalVM Native Image.
 *
 * <p>By shadowing the original class and replacing its GUI functionality with a minimal
 * implementation, Swing and its associated dependencies can be excluded from the native image,
 * reducing the resulting executable size.
 *
 * <p>GUI functionality is intentionally unavailable in this build.
 */
public final class Gui {

  private Gui() {}

  /**
   * Stub implementation of the original GUI entry point.
   *
   * @param barFile path to the BAR file
   * @param deviceIp IP address of the target BlackBerry device
   * @param password device password
   * @throws UnsupportedOperationException always, because GUI functionality is not included in this
   *     build
   */
  public static void run(String barFile, String deviceIp, String password) {
    throw new UnsupportedOperationException("GUI is not available in this build");
  }
}
