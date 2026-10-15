package com.jbraddockm.core.daemon;

import java.util.Objects;

/**
 * Identifies the tool behind a daemon in its {@code hello} event.
 *
 * @param tool the tool name, for example {@code bb-link}
 * @param version the tool version
 */
public record ServiceInfo(String tool, String version) {

  /**
   * Validates the identification.
   *
   * @throws NullPointerException if a component is {@code null}
   */
  public ServiceInfo {
    Objects.requireNonNull(tool, "tool");
    Objects.requireNonNull(version, "version");
  }
}
