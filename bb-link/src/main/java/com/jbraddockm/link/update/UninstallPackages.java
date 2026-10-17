package com.jbraddockm.link.update;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.cgi.Conversation;
import com.jbraddockm.link.session.DeviceCommand;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes applications from the device by their package ids.
 *
 * <p>Unless told not to, the conversation first reads the device's package list and refuses, before
 * anything is sent to remove, an id that is not installed and an id of firmware, which is never
 * removed this way. The list cannot tell a built-in application from one that was installed, so the
 * device itself decides about those.
 *
 * <p>The command changes the device, and removes the application's data with it. It is never
 * repeated after a dropped connection.
 *
 * @param packageIds the packages to remove, in the order given
 * @param check whether to look at the device's package list first; {@code false} is for
 *     diagnostics, to see how the device answers an id that the check would have refused
 */
public record UninstallPackages(List<String> packageIds, boolean check)
    implements DeviceCommand<UninstallResult> {

  private static final Pattern PACKAGE_ID = Pattern.compile("[A-Za-z0-9._-]{1,128}");

  /**
   * Validates the command.
   *
   * @throws InvalidArgumentException naming {@code packageIds} or {@code packageIds[n]} if there is
   *     no package, if an id is not made of letters, digits, dots, dashes and underscores (at most
   *     128), or if an id is given twice
   */
  public UninstallPackages {
    packageIds = List.copyOf(packageIds);
    if (packageIds.isEmpty()) {
      throw new InvalidArgumentException("packageIds", "must name at least one package");
    }
    var seen = new HashMap<String, Integer>();
    for (var index = 0; index < packageIds.size(); index++) {
      var packageId = packageIds.get(index);
      if (!PACKAGE_ID.matcher(packageId).matches()) {
        throw new InvalidArgumentException(
            "packageIds[" + index + "]",
            "is not a package id: letters, digits, dots, dashes and underscores, at most 128");
      }
      var earlier = seen.putIfAbsent(packageId, index);
      if (earlier != null) {
        throw new InvalidArgumentException(
            "packageIds[" + index + "]", "is the same package as packageIds[" + earlier + "]");
      }
    }
  }

  /**
   * Prepares the removal of packages, after looking at the device's package list.
   *
   * @param packageIds the packages to remove
   * @throws InvalidArgumentException as in the constructor
   */
  public UninstallPackages(List<String> packageIds) {
    this(packageIds, true);
  }

  @Override
  public Conversation<UninstallResult> conversation() {
    return new UninstallConversation(packageIds, check);
  }

  @Override
  public boolean readOnly() {
    return false;
  }
}
