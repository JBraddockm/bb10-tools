package com.jbraddockm.link.session;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.CommandResult;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.daemon.ServiceSession;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.cgi.CgiResponse;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A logged-in connection to one device that runs commands one at a time.
 *
 * <p>The login happens when the first command runs and is renewed when the device forgets it. The
 * rules for trying again are strict, because commands change the device:
 *
 * <ul>
 *   <li>A rejected login or a wrong password is never repeated.
 *   <li>If the device rejects the session on the <em>first</em> request of a command, nothing has
 *       happened yet, so the session logs in again and sends the command once more.
 *   <li>If the connection dropped, only commands that merely read are sent a second time.
 *   <li>Everything else fails at once.
 * </ul>
 */
public final class DeviceSession implements ServiceSession {

  private final DeviceTarget target;
  private final ExchangeObserver observer;
  private final String tunnelSmbHost;
  private final ReentrantLock lock = new ReentrantLock();
  private CgiTransport transport;
  private SmbCredentials smbCredentials;
  private boolean closed;

  private DeviceSession(DeviceTarget target, ExchangeObserver observer, String tunnelSmbHost) {
    this.target = Objects.requireNonNull(target, "target");
    this.observer = Objects.requireNonNull(observer, "observer");
    this.tunnelSmbHost = tunnelSmbHost;
  }

  /**
   * Creates a session. Nothing is sent to the device until the first command runs.
   *
   * @param target the device and its password
   * @param observer is told about every exchange
   * @return the session
   */
  public static DeviceSession forTarget(DeviceTarget target, ExchangeObserver observer) {
    return forTarget(target, observer, null);
  }

  /**
   * Creates a session whose Samba share address over the Wi-Fi tunnel is {@code tunnelSmbHost} —
   * the inner IPv4 the externally-established tunnel bound for this device — rather than the
   * default {@code 169.254.1.1}. This is how concurrent tunnels to different devices get distinct
   * share addresses; it has no effect on a USB session, which reaches the share at its own address.
   *
   * @param target the device and its password
   * @param observer is told about every exchange
   * @param tunnelSmbHost the tunnel's inner IPv4 for the share, or {@code null} for the default
   * @return the session
   */
  public static DeviceSession forTarget(
      DeviceTarget target, ExchangeObserver observer, String tunnelSmbHost) {
    return new DeviceSession(target, observer, tunnelSmbHost);
  }

  /**
   * Creates a session that records nothing.
   *
   * @param target the device and its password
   * @return the session
   */
  public static DeviceSession forTarget(DeviceTarget target) {
    return forTarget(target, ExchangeObserver.NONE);
  }

  /**
   * Replays recorded device answers through a command's conversation, without a device.
   *
   * @param command the command that produced the answers
   * @param documents the recorded response bodies, one per request, in order
   * @param listener receives log and progress events
   * @param <R> the result type
   * @return how the command ended
   */
  public static <R extends CommandResult> CommandOutcome<R> replay(
      DeviceCommand<R> command, List<String> documents, CommandListener listener) {
    var remaining = new ArrayDeque<>(documents);
    try {
      return ConversationRunner.run(
          command.conversation(),
          (request, _) -> {
            var next = remaining.poll();
            if (next == null) {
              throw new IOException("the recording has no answer for this request");
            }
            return new CgiResponse(
                200, null, next, null); // a recording does not keep the content type
          },
          listener);
    } catch (StaleSessionException stale) {
      return CommandOutcome.failed(rejectedSession(stale, false));
    } catch (IOException failure) {
      return CommandOutcome.failed(FailureClassifier.classify(failure));
    }
  }

  private static ServiceError rejectedSession(
      StaleSessionException stale, boolean certificateAlone) {
    if (certificateAlone) {
      return new ServiceError(
          CoreErrorCode.AUTH_FAILED,
          "The device did not accept the client certificate as authentication");
    }
    return new ServiceError(
        CoreErrorCode.AUTH_FAILED,
        stale.firstExchange()
            ? "The device rejected the session right after a new login"
            : "The device ended the session while the command was running");
  }

  /**
   * A session to the same device through another scheme, port and authentication, such as the
   * certificate port after an enrolment through the password port. It shares the observer, logs in
   * on its first command, and is closed on its own: closing it does not affect this session.
   *
   * @param scheme the scheme of the new session
   * @param port the port of the new session
   * @param authentication how the new session logs in
   * @return the session; nothing has been sent yet
   */
  public DeviceSession sibling(TransportScheme scheme, int port, Authentication authentication) {
    return new DeviceSession(
        new DeviceTarget(target.host(), port, scheme, authentication), observer, tunnelSmbHost);
  }

  /**
   * Returns the device this session talks to.
   *
   * @return the target
   */
  public DeviceTarget target() {
    return target;
  }

  /**
   * Returns the file share credentials of the most recent login.
   *
   * <p>The password changes with every login, including the automatic ones that replace a session
   * the device has forgotten, so a copy taken earlier may no longer work.
   *
   * @return the credentials, or empty if the device did not hand any out
   */
  public Optional<SmbCredentials> smbCredentials() {
    lock.lock();
    try {
      return Optional.ofNullable(smbCredentials);
    } finally {
      lock.unlock();
    }
  }

  /**
   * Runs a command, logging in first if necessary.
   *
   * @param command the command
   * @param listener receives log and progress events
   * @param <R> the result type
   * @return how the command ended; failures are outcomes, not exceptions
   */
  public <R extends CommandResult> CommandOutcome<R> execute(
      DeviceCommand<R> command, CommandListener listener) {
    lock.lock();
    try {
      return executeLocked(command, listener);
    } finally {
      lock.unlock();
    }
  }

  private <R extends CommandResult> CommandOutcome<R> executeLocked(
      DeviceCommand<R> command, CommandListener listener) {
    var loginRenewed = false;
    var connectionRenewed = false;
    while (true) {
      try {
        ensureLoggedIn(listener);
        return ConversationRunner.run(
            command.conversation(),
            (request, observer) -> transport.send(request, observer),
            listener);
      } catch (StaleSessionException stale) {
        discardTransport();
        if (stale.firstExchange() && !loginRenewed) {
          loginRenewed = true;
          listener.onLog(LogLevel.DEBUG, "The device forgot the session; logging in again");
          continue;
        }
        return CommandOutcome.failed(
            rejectedSession(stale, target.authentication().certificateAlone()));
      } catch (LoginException | IOException | RuntimeException failure) {
        if (!FailureClassifier.leavesSessionUsable(failure)) {
          discardTransport();
        }
        if (command.readOnly()
            && !connectionRenewed
            && FailureClassifier.isStaleConnection(failure)) {
          connectionRenewed = true;
          listener.onLog(LogLevel.DEBUG, "The connection was closed; connecting again");
          continue;
        }
        return CommandOutcome.failed(
            FailureClassifier.classify(
                failure, target.authentication().certificateToOffer().isPresent(), target.path()));
      }
    }
  }

  private void ensureLoggedIn(CommandListener listener) throws LoginException, IOException {
    if (closed) {
      throw new IllegalStateException("the session is closed");
    }
    if (transport == null) {
      var fresh = new CgiTransport(target, observer);
      try {
        smbCredentials = login(fresh, listener);
      } catch (LoginException | IOException | RuntimeException failure) {
        fresh.close();
        throw failure;
      }
      transport = fresh;
    }
  }

  /**
   * The address at which the device's Samba share is reachable, regardless of how this session
   * connected: the connection address over USB, and over the Wi-Fi tunnel the inner IPv4 the tunnel
   * bound for this device ({@code tunnelSmbHost}) or {@code 169.254.1.1} when none was given.
   */
  String smbHost() {
    if (target.path() == ConnectionPath.TUNNEL) {
      return tunnelSmbHost != null ? tunnelSmbHost : "169.254.1.1";
    }
    return target.host();
  }

  private SmbCredentials login(CgiTransport fresh, CommandListener listener)
      throws LoginException, IOException {
    try {
      var credentials = Login.perform(fresh).orElse(null);
      return credentials != null
          ? new SmbCredentials(
              credentials.user(),
              credentials.password(),
              credentials.workgroup(),
              credentials.netBios(),
              smbHost())
          : null;
    } catch (PasswordRequiredException asked) {
      if (!target.authentication().certificateAlone()) {
        throw asked;
      }
      listener.onLog(
          LogLevel.WARN,
          "The device asked for a password during the login; continuing with the certificate alone,"
              + " without share credentials");
      return null;
    }
  }

  private void discardTransport() {
    if (transport != null) {
      transport.close();
      transport = null;
    }
  }

  @Override
  public void close() {
    lock.lock();
    try {
      closed = true;
      discardTransport();
    } finally {
      lock.unlock();
    }
  }
}
