package com.jbraddockm.output;

import com.jbraddockm.internal.Json;
import java.util.Locale;
import java.util.Map;

/**
 * What an install did.
 *
 * <p>The package identity ({@code fullName}, {@code name}, {@code id}, {@code version}) is {@code
 * null} when the install did not report it: a pure file sync prints no identity, and a failed
 * install prints empty values.
 *
 * @param success whether the final outcome is a success; the last outcome wins when the tool
 *     retried
 * @param fullName the package name, a dot, and the package id with each {@code -} written as {@code
 *     .}
 * @param name the package name, or {@code null} if it cannot be derived
 * @param id the package id
 * @param version the package version
 * @param strategy how the app reached the device, or {@code null} if no install or sync took place
 * @param nothingToSync whether a file sync found every file already up to date
 * @param launched whether a launch was requested and the device launched the app
 * @param launchResult the device's answer to the launch (a number, probably the process id), or
 *     {@code null}
 * @param fellBackToFull whether a lighter install failed and the whole package was installed
 *     instead
 * @param syncDisabledReason why the incremental install was not attempted, or {@code null}
 * @param syncFailureReason why the incremental install failed or could not be prepared, or {@code
 *     null}
 * @param failureLine the line that reported the final failure, or {@code null}
 */
public record InstallResult(
    boolean success,
    String fullName,
    String name,
    String id,
    String version,
    Strategy strategy,
    boolean nothingToSync,
    boolean launched,
    String launchResult,
    boolean fellBackToFull,
    String syncDisabledReason,
    String syncFailureReason,
    String failureLine)
    implements CommandResult {

  @Override
  public Map<String, Object> toJson() {
    return Json.object(
        "success", success,
        "fullName", fullName,
        "name", name,
        "id", id,
        "version", version,
        "strategy", strategy == null ? null : strategy.wireName(),
        "nothingToSync", nothingToSync,
        "launched", launched,
        "launchResult", launchResult,
        "fellBackToFull", fellBackToFull,
        "syncDisabledReason", syncDisabledReason,
        "syncFailureReason", syncFailureReason,
        "failureLine", failureLine);
  }

  /** How an app reached the device. */
  public enum Strategy {

    /** The whole package was uploaded and installed. */
    FULL,

    /** A manifest-only package was installed, then the files were synchronised. */
    MINIBAR,

    /** The app was already installed and only changed files were synchronised. */
    SYNC;

    /**
     * Returns the lower-case name used in the wire protocol.
     *
     * @return {@code full}, {@code minibar} or {@code sync}
     */
    public String wireName() {
      return name().toLowerCase(Locale.ROOT);
    }
  }
}
