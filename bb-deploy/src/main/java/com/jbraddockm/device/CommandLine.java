package com.jbraddockm.device;

import java.util.ArrayList;
import java.util.List;

/** Assembles the argument list of one deploy command. */
final class CommandLine {

  private final List<String> arguments = new ArrayList<>();

  private CommandLine() {}

  static CommandLine of(String... initial) {
    return new CommandLine().add(initial);
  }

  CommandLine add(String... values) {
    arguments.addAll(List.of(values));
    return this;
  }

  CommandLine addAll(List<String> values) {
    arguments.addAll(values);
    return this;
  }

  CommandLine addIf(boolean condition, String... values) {
    return condition ? add(values) : this;
  }

  /** Adds {@code flag value} unless the value is {@code null}. */
  CommandLine addOption(String flag, Object value) {
    return value == null ? this : add(flag, value.toString());
  }

  List<String> build() {
    return List.copyOf(arguments);
  }
}
