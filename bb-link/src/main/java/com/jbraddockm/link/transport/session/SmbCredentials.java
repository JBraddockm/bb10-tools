package com.jbraddockm.link.transport.session;

import com.jbraddockm.core.json.Json;
import java.util.Map;
import java.util.Objects;

/**
 * The credentials of the device's file share, handed out by the device on every successful login.
 *
 * <p>They give access to the contents of the device, even when neither Wi-Fi storage nor USB mass
 * storage is switched on. The username is fixed; the password changes with every login, so these
 * are the credentials of the most recent login and an earlier copy may no longer work.
 *
 * <p>They are returned to the client that logged in, on purpose. Everywhere else, in traces and in
 * raw output, they are masked, and {@link #toString()} never shows the password.
 *
 * @param user the username
 * @param password the password of this login
 * @param workgroup the workgroup; may be {@code null}
 * @param netBios the NetBIOS name exactly as the device writes it, which is the placeholder {@code
 *     %h} (the host name) on the captured device; may be {@code null}
 * @param host the address at which the share is reachable: the connection address for USB, {@code
 *     169.254.1.1} for the tunnel
 */
public record SmbCredentials(
    String user, String password, String workgroup, String netBios, String host) {

  /**
   * Validates the credentials.
   *
   * @throws NullPointerException if the user or the password is {@code null}
   */
  public SmbCredentials {
    Objects.requireNonNull(user, "user");
    Objects.requireNonNull(password, "password");
    Objects.requireNonNull(host, "host");
  }

  /**
   * Returns the wire representation.
   *
   * @return an insertion-ordered map; absent values are {@code null}
   */
  public Map<String, Object> toJson() {
    return Json.object(
        "user",
        user,
        "password",
        password,
        "workgroup",
        workgroup,
        "netBios",
        netBios,
        "host",
        host);
  }

  /** Never reveals the password. */
  @Override
  public String toString() {
    return "SmbCredentials[user=" + user + ", password=***]";
  }
}
