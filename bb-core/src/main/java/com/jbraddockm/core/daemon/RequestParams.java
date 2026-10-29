package com.jbraddockm.core.daemon;

import com.jbraddockm.core.error.InvalidArgumentException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Typed access to the {@code params} object of a request. Every problem is an {@link
 * InvalidArgumentException}, which the server answers with {@code INVALID_ARGUMENT}.
 */
public final class RequestParams {

  private final Map<?, ?> values;

  /**
   * Wraps the parameters of one request.
   *
   * @param values the decoded {@code params} object; keys are expected to be strings
   */
  public RequestParams(Map<?, ?> values) {
    this.values = values;
  }

  /**
   * Returns the names of all parameters that were sent, so that a command can refuse the ones it
   * does not know instead of ignoring them.
   *
   * @return the parameter names
   */
  public Set<String> names() {
    var names = new LinkedHashSet<String>();
    values.keySet().forEach(name -> names.add(String.valueOf(name)));
    return names;
  }

  /**
   * Reads an optional nested object.
   *
   * @param name the parameter name
   * @return the object's members, or empty if the parameter is absent or {@code null}
   * @throws InvalidArgumentException if the value is present but not an object
   */
  public Optional<RequestParams> optionalObject(String name) {
    return switch (values.get(name)) {
      case null -> Optional.empty();
      case Map<?, ?> members -> Optional.of(new RequestParams(members));
      default -> throw new InvalidArgumentException(name, "must be an object");
    };
  }

  /**
   * Reads a required nested object.
   *
   * @param name the parameter name
   * @return the object's members
   * @throws InvalidArgumentException if the parameter is absent or not an object
   */
  public RequestParams requireObject(String name) {
    return optionalObject(name)
        .orElseThrow(() -> new InvalidArgumentException(name, "is required"));
  }

  /**
   * Reads a required nested object as a map of its members, keyed by name. For a command that
   * forwards the object to the device verbatim rather than interpreting its shape.
   *
   * @param name the parameter name
   * @return the members, in the order they were sent
   * @throws InvalidArgumentException if the parameter is absent or not an object
   */
  public Map<String, Object> requireObjectMap(String name) {
    return switch (values.get(name)) {
      case null -> throw new InvalidArgumentException(name, "is required");
      case Map<?, ?> members -> {
        var copy = new LinkedHashMap<String, Object>();
        members.forEach((key, value) -> copy.put(String.valueOf(key), value));
        yield copy;
      }
      default -> throw new InvalidArgumentException(name, "must be an object");
    };
  }

  /**
   * Reads an optional string.
   *
   * @param name the parameter name
   * @return the value, or empty if the parameter is absent or {@code null}
   * @throws InvalidArgumentException if the parameter is present but not a string
   */
  public Optional<String> optionalString(String name) {
    return switch (values.get(name)) {
      case null -> Optional.empty();
      case String text -> Optional.of(text);
      default -> throw new InvalidArgumentException(name, "must be a string");
    };
  }

  /**
   * Reads a required, non-blank string.
   *
   * @param name the parameter name
   * @return the value
   * @throws InvalidArgumentException if the parameter is absent, blank or not a string
   */
  public String requireString(String name) {
    return optionalString(name)
        .filter(text -> !text.isBlank())
        .orElseThrow(() -> new InvalidArgumentException(name, "is required"));
  }

  /**
   * Reads an optional file system path.
   *
   * @param name the parameter name
   * @return the path, or empty if the parameter is absent
   * @throws InvalidArgumentException if the value is not a string or not a valid path
   */
  public Optional<Path> optionalPath(String name) {
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

  /**
   * Reads a required file system path.
   *
   * @param name the parameter name
   * @return the path
   * @throws InvalidArgumentException if the parameter is absent or not a valid path
   */
  public Path requirePath(String name) {
    requireString(name);
    return optionalPath(name).orElseThrow();
  }

  /**
   * Reads a list of non-blank strings.
   *
   * @param name the parameter name
   * @return the strings in order; empty if the parameter is absent
   * @throws InvalidArgumentException if the value is not an array of non-blank strings
   */
  public List<String> optionalStrings(String name) {
    return switch (values.get(name)) {
      case null -> List.of();
      case List<?> items -> {
        var strings = new ArrayList<String>();
        for (var item : items) {
          if (item instanceof String text && !text.isBlank()) {
            strings.add(text);
          } else {
            throw new InvalidArgumentException(name, "must contain only non-empty strings");
          }
        }
        yield List.copyOf(strings);
      }
      default -> throw new InvalidArgumentException(name, "must be an array of strings");
    };
  }

  /**
   * Reads a non-empty list of non-blank strings.
   *
   * @param name the parameter name
   * @return the strings in order
   * @throws InvalidArgumentException if the parameter is absent, empty or malformed
   */
  public List<String> requireStrings(String name) {
    var strings = optionalStrings(name);
    if (strings.isEmpty()) {
      throw new InvalidArgumentException(name, "is required and must not be empty");
    }
    return strings;
  }

  /**
   * Reads a boolean flag.
   *
   * @param name the parameter name
   * @return the value; {@code false} if the parameter is absent
   * @throws InvalidArgumentException if the value is not a boolean
   */
  public boolean flag(String name) {
    return flag(name, false);
  }

  /**
   * Reads a boolean flag that has a default other than {@code false}.
   *
   * @param name the parameter name
   * @param fallback the value if the parameter is absent
   * @return the value, or the fallback if the parameter is absent
   * @throws InvalidArgumentException if the value is not a boolean
   */
  public boolean flag(String name, boolean fallback) {
    return switch (values.get(name)) {
      case null -> fallback;
      case Boolean value -> value;
      default -> throw new InvalidArgumentException(name, "must be a boolean");
    };
  }

  /**
   * Reads an optional integer with a range.
   *
   * @param name the parameter name
   * @param minimum the smallest accepted value
   * @param maximum the largest accepted value
   * @return the value, or empty if the parameter is absent
   * @throws InvalidArgumentException if the value is not an integer or outside the range
   */
  public OptionalInt optionalInteger(String name, int minimum, int maximum) {
    return switch (values.get(name)) {
      case null -> OptionalInt.empty();
      case Long number when number >= minimum && number <= maximum ->
          OptionalInt.of(number.intValue());
      case Long _ ->
          throw new InvalidArgumentException(
              name, "must be between " + minimum + " and " + maximum);
      default -> throw new InvalidArgumentException(name, "must be an integer");
    };
  }

  /**
   * Reads an integer with a default and a range.
   *
   * @param name the parameter name
   * @param defaultValue the value used if the parameter is absent
   * @param minimum the smallest accepted value
   * @param maximum the largest accepted value
   * @return the value
   * @throws InvalidArgumentException if the value is not an integer or outside the range
   */
  public int integer(String name, int defaultValue, int minimum, int maximum) {
    return switch (values.get(name)) {
      case null -> defaultValue;
      case Long number when number >= minimum && number <= maximum -> number.intValue();
      case Long _ ->
          throw new InvalidArgumentException(
              name, "must be between " + minimum + " and " + maximum);
      default -> throw new InvalidArgumentException(name, "must be an integer");
    };
  }
}
