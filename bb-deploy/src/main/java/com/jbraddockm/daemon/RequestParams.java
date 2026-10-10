package com.jbraddockm.daemon;

import com.jbraddockm.device.InvalidArgumentException;
import com.jbraddockm.device.PackageRef;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Typed access to the {@code params} object of a request. Every problem is an {@link
 * InvalidArgumentException}.
 */
final class RequestParams {

  private final Map<?, ?> values;

  RequestParams(Map<?, ?> values) {
    this.values = values;
  }

  Optional<String> optionalString(String name) {
    return switch (values.get(name)) {
      case null -> Optional.empty();
      case String text -> Optional.of(text);
      default -> throw new InvalidArgumentException(name, "must be a string");
    };
  }

  String requireString(String name) {
    return optionalString(name)
        .filter(text -> !text.isBlank())
        .orElseThrow(() -> new InvalidArgumentException(name, "is required"));
  }

  Optional<Path> optionalPath(String name) {
    return optionalString(name)
        .map(
            text -> {
              try {
                return Path.of(text);
              } catch (InvalidPathException _) {
                throw new InvalidArgumentException(name, "is not a valid path");
              }
            });
  }

  Path requirePath(String name) {
    requireString(name);
    return optionalPath(name).orElseThrow();
  }

  boolean flag(String name) {
    return switch (values.get(name)) {
      case null -> false;
      case Boolean value -> value;
      default -> throw new InvalidArgumentException(name, "must be a boolean");
    };
  }

  int integer(String name, int defaultValue, int minimum, int maximum) {
    return switch (values.get(name)) {
      case null -> defaultValue;
      case Long number when number >= minimum && number <= maximum -> number.intValue();
      case Long _ ->
          throw new InvalidArgumentException(
              name, "must be between " + minimum + " and " + maximum);
      default -> throw new InvalidArgumentException(name, "must be an integer");
    };
  }

  /**
   * Reads the application a command acts on. Exactly one of {@code bar}, {@code packageName} with
   * {@code packageId}, or {@code packageFullName} must be given.
   */
  PackageRef packageRef() {
    var bar = optionalPath("bar");
    var name = optionalString("packageName");
    var id = optionalString("packageId");
    var fullName = optionalString("packageFullName");

    var forms =
        Stream.of(bar.isPresent(), name.isPresent() || id.isPresent(), fullName.isPresent())
            .filter(given -> given)
            .count();
    if (forms != 1) {
      throw new InvalidArgumentException(
          "bar",
          "must be the only package identification: give exactly one of bar, packageName+packageId, packageFullName");
    }
    if (bar.isPresent()) {
      return new PackageRef.BarFile(bar.get());
    }
    if (fullName.isPresent()) {
      return new PackageRef.FullName(fullName.get());
    }
    return new PackageRef.NameAndId(name.orElse(null), id.orElse(null));
  }
}
