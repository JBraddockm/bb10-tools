package com.jbraddockm.device;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Identifies the application a command acts on. */
public sealed interface PackageRef {

  /**
   * Returns the command-line arguments that carry this reference.
   *
   * @return the arguments, in order
   */
  List<String> arguments();

  /**
   * Ensures that the package name is known, which some commands need in addition to the id.
   *
   * @throws InvalidArgumentException if this is a name-and-id reference without a name
   */
  default void requireName() {
    if (this instanceof NameAndId(var name, _) && name == null) {
      throw new InvalidArgumentException("packageName", "is required with packageId");
    }
  }

  /**
   * An application identified by the BAR file it comes from; the identity is read from its
   * manifest.
   *
   * @param bar the path of the BAR file
   */
  record BarFile(Path bar) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws NullPointerException if {@code bar} is {@code null}
     */
    public BarFile {
      Objects.requireNonNull(bar, "bar");
    }

    @Override
    public List<String> arguments() {
      return List.of("-package", bar.toString());
    }
  }

  /**
   * An application identified by its package id and, for most commands, its package name.
   *
   * @param name the package name, or {@code null} where the command does not need it
   * @param id the real package id, including any {@code -}
   */
  record NameAndId(String name, String id) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws InvalidArgumentException if the id is missing
     */
    public NameAndId {
      if (id == null || id.isBlank()) {
        throw new InvalidArgumentException("packageId", "is required with packageName");
      }
    }

    @Override
    public List<String> arguments() {
      return name == null
          ? List.of("-package-id", id)
          : List.of("-package-id", id, "-package-name", name);
    }
  }

  /**
   * An application identified by its full name, the package name followed by a dot and the id.
   *
   * <p>The Deploy tool assumes the id is exactly the last 27 characters, which is wrong for the
   * 28-character ids that exist on real devices; prefer {@link NameAndId}.
   *
   * @param fullName the full name
   */
  record FullName(String fullName) implements PackageRef {

    /**
     * Creates the reference.
     *
     * @throws NullPointerException if {@code fullName} is {@code null}
     */
    public FullName {
      Objects.requireNonNull(fullName, "fullName");
    }

    @Override
    public List<String> arguments() {
      return List.of("-package-fullname", fullName);
    }
  }
}
