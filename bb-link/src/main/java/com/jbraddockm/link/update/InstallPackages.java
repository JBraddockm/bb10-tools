package com.jbraddockm.link.update;

import com.jbraddockm.core.bar.BarInfo;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.session.DeviceCommand;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Installs applications from bars. The bars have been read already ({@link BarSelection}), so that
 * the conversation with the device is a pure state machine that does no file reading of its own.
 *
 * <p>Only applications are installed for now. Firmware, which the device installs through the same
 * program but in another mode and which ends with a restart, is read and recognised, and refused
 * with a message that says so.
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
   *     if a bar is not an application, or if two bars are the same package
   */
  public InstallPackages {
    bars = List.copyOf(bars);
    Objects.requireNonNull(pollInterval, "pollInterval");
    if (bars.isEmpty()) {
      throw new InvalidArgumentException("files", "must name at least one bar");
    }
    var seen = new HashMap<String, Integer>();
    for (var index = 0; index < bars.size(); index++) {
      var bar = bars.get(index);
      var refusal = refusal(bar);
      if (refusal != null) {
        throw new InvalidArgumentException("files[" + index + "]", refusal);
      }
      if (bar.packageId() != null) {
        var earlier = seen.putIfAbsent(bar.packageId(), index);
        if (earlier != null) {
          throw new InvalidArgumentException(
              "files[" + index + "]", "is the same package as files[" + earlier + "]");
        }
      }
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

  /** Returns why a bar cannot be installed, or {@code null} if it can. */
  private static String refusal(BarInfo bar) {
    return switch (bar.kind()) {
      case APPLICATION -> null;
      case OPERATING_SYSTEM ->
          "is an operating system image; installing firmware is not supported yet";
      case RADIO -> "is a radio image; installing firmware is not supported yet";
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
    return new InstallConversation(bars, pollInterval);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
