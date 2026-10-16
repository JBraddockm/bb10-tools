package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.bar.BarInspector;
import com.jbraddockm.core.bar.InvalidBarException;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The bars that a request names, read: either all of them, or the first reason why one cannot be
 * used.
 */
public sealed interface BarSelection {

  /**
   * Every bar was read.
   *
   * @param bars what the manifests say, in the order of the request
   */
  record Selected(List<BarInfo> bars) implements BarSelection {

    /** Takes an immutable copy of the list. */
    public Selected {
      bars = List.copyOf(bars);
    }
  }

  /**
   * A bar could not be read, and nothing is to be done.
   *
   * @param error {@code INVALID_ARGUMENT} for a file that is not a usable bar, naming it as {@code
   *     files[n]}; {@code IO_ERROR} for a file that cannot be read
   */
  record Refused(ServiceError error) implements BarSelection {}

  /**
   * Reads the manifests of the bars.
   *
   * @param names the paths, as the request gave them
   * @return the bars, or why one is not usable
   */
  static BarSelection of(List<String> names) {
    var bars = new ArrayList<BarInfo>();
    for (var index = 0; index < names.size(); index++) {
      var parameter = "files[" + index + "]";
      try {
        bars.add(BarInspector.inspect(Path.of(names.get(index))));
      } catch (InvalidPathException _) {
        return new Refused(
            new InvalidArgumentException(parameter, "is not a valid path").toServiceError());
      } catch (InvalidBarException failure) {
        return new Refused(
            new InvalidArgumentException(parameter, "is not a usable bar: " + failure.getMessage())
                .toServiceError());
      } catch (IOException failure) {
        return new Refused(
            new ServiceError(
                CoreErrorCode.IO_ERROR,
                switch (failure) {
                  case NoSuchFileException _ -> "The file does not exist";
                  case AccessDeniedException _ -> "The file cannot be read: access is denied";
                  default -> "The file cannot be read: " + failure.getMessage();
                },
                Json.object("param", parameter, "file", names.get(index))));
      }
    }
    return new Selected(bars);
  }
}
