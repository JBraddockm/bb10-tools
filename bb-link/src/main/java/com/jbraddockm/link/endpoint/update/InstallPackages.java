package com.jbraddockm.link.endpoint.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.transport.cgi.Conversation;
import com.jbraddockm.link.transport.cgi.DeviceCommand;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Installs packages from bars. The bars have been read already ({@link BarSelection}), so that the
 * conversation with the device is a pure state machine that does no file reading of its own.
 *
 * <p>Applications are installed in mode {@code bar}; firmware (the operating system and the radio)
 * is installed in mode {@code os}, and each file is uploaded as {@code type=bar} (the operating
 * system) or {@code type=radio}. A single command installs either applications or firmware, never
 * both.
 *
 * <p>The command changes the device, so it is never repeated after a dropped connection.
 *
 * @param bars what the manifests of the bars say, in the order they are sent
 * @param pollInterval how long to wait between two questions about how far the device is
 */
public record InstallPackages(List<BarInfo> bars, Duration pollInterval)
    implements DeviceCommand<InstallResult> {

  /** How often the device is asked how far it is. */
  public static final Duration DEFAULT_POLL_INTERVAL = Duration.ofSeconds(1);

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException naming {@code files} or {@code files[n]} if there is no bar,
   *     if a bar cannot be installed, if applications and firmware are mixed, or if two bars are
   *     the same package
   */
  public InstallPackages {
    bars = List.copyOf(bars);
    Objects.requireNonNull(pollInterval, "pollInterval");
    if (bars.isEmpty()) {
      throw new InvalidArgumentException("files", "must name at least one bar");
    }
    var seen = new HashMap<String, Integer>();
    boolean hasApplication = false;
    boolean hasFirmware = false;
    for (var index = 0; index < bars.size(); index++) {
      var bar = bars.get(index);
      var refusal = refusal(bar);
      if (refusal != null) {
        throw new InvalidArgumentException("files[" + index + "]", refusal);
      }
      if (bar.kind().isFirmware()) {
        hasFirmware = true;
      } else {
        hasApplication = true;
      }
      if (bar.packageId() != null) {
        var earlier = seen.putIfAbsent(bar.packageId(), index);
        if (earlier != null) {
          throw new InvalidArgumentException(
              "files[" + index + "]", "is the same package as files[" + earlier + "]");
        }
      }
    }
    if (hasApplication && hasFirmware) {
      throw new InvalidArgumentException(
          "files", "mixes applications and firmware; install them in separate commands");
    }
  }

  /**
   * Prepares the installation of bars, asking the device about its progress once a second.
   *
   * @param bars what the manifests of the bars say
   * @return the command
   * @throws InvalidArgumentException as in the constructor
   */
  public static InstallPackages of(List<BarInfo> bars) {
    return new InstallPackages(bars, DEFAULT_POLL_INTERVAL);
  }

  /**
   * Tells whether this command installs firmware rather than applications.
   *
   * @return {@code true} for an operating system or radio install
   */
  public boolean isFirmware() {
    return bars.getFirst().kind().isFirmware();
  }

  /** Returns why a bar cannot be installed, or {@code null} if it can. */
  private static String refusal(BarInfo bar) {
    return switch (bar.kind()) {
      case APPLICATION, OPERATING_SYSTEM, RADIO -> null;
      case OTHER ->
          "is a package of type '" + bar.packageType() + "', which cannot be installed this way";
    };
  }

  /**
   * Returns the number of bytes that the installation uploads, which the device is told first.
   *
   * @return the sum of the sizes of the bars
   */
  public long totalBytes() {
    return bars.stream().mapToLong(BarInfo::sizeBytes).sum();
  }

  @Override
  public Conversation<InstallResult> conversation() {
    return new InstallConversation(bars, isFirmware(), pollInterval);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
