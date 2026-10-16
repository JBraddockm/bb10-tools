package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

/**
 * Looks at a bar without a device. It is the pre-flight of an installation, on its own: what the
 * package is, how big it is, and, if asked, whether BlackBerry's rules accept its manifest.
 */
public final class InspectPackage {

  private InspectPackage() {}

  /**
   * Inspects a bar.
   *
   * @param file the bar
   * @param validate whether to apply BlackBerry's rules to the manifest as well
   * @return what was found, or why the file cannot be used: {@code INVALID_ARGUMENT} for a file
   *     that is not a usable bar, {@code IO_ERROR} for a file that cannot be read
   */
  public static CommandOutcome<PackageInspection> inspect(Path file, boolean validate) {
    try {
      var info = BarInspector.inspect(file);
      var validation = validate ? BarInspector.validate(file) : null;
      return CommandOutcome.succeeded(new PackageInspection(info, validation));
    } catch (InvalidBarException failure) {
      return CommandOutcome.failed(
          new InvalidArgumentException("file", "is not a usable bar: " + failure.getMessage())
              .toServiceError());
    } catch (IOException failure) {
      return CommandOutcome.failed(
          new ServiceError(
              CoreErrorCode.IO_ERROR, describe(failure), Json.object("file", file.toString())));
    }
  }

  private static String describe(IOException failure) {
    return switch (failure) {
      case NoSuchFileException _ -> "The file does not exist";
      case AccessDeniedException _ -> "The file cannot be read: access is denied";
      default -> "The file cannot be read: " + failure.getMessage();
    };
  }
}
