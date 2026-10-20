package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Provides the real {@code .bar} archives used by the update tests: copies them to a test folder
 * and reads them as the daemon does.
 */
final class BarResources {

  private BarResources() {}

  /**
   * Copies a bar of the resources into a folder.
   *
   * @param name the name of the resource
   * @param folder the folder
   * @return the copy
   */
  static Path copy(String name, Path folder) {
    return Resources.copyTo(BarResources.class, name, folder);
  }

  /**
   * Copies a bar of the resources into a folder and reads it.
   *
   * @param name the name of the resource
   * @param folder the folder
   * @return what the daemon knows of the bar
   * @throws IOException if the copy cannot be read
   * @throws InvalidBarException if the copy is not a usable bar
   */
  static BarInfo inspect(String name, Path folder) throws IOException, InvalidBarException {
    return BarInspector.inspect(copy(name, folder));
  }
}
