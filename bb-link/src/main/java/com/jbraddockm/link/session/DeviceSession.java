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
  private final ReentrantLock lock = new ReentrantLock();
  private CgiTransport transport;
  private SmbCredentials smbCredentials;
  private boolean closed;

  private DeviceSession(DeviceTarget target, ExchangeObserver observer) {
    this.target = Objects.requireNonNull(target, "target");
    this.observer = Objects.requireNonNull(observer, "observer");
  }

  /**
   * Creates a session. Nothing is sent to the device until the first command runs.
   *
   * @param target the device and its password
   * @param observer is told about every exchange
   * @return the session
   */
  public static DeviceSession forTarget(DeviceTarget target, ExchangeObserver observer) {
    return new DeviceSession(target, observer);
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
            command.conversation(), request -> transport.send(request), listener);
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
                failure, target.authentication().certificateToOffer().isPresent()));
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
   * Logs in. A session that relies on the certificate alone never answers a password challenge: if
   * the device asks for a password, that is reported as a warning and the session carries on with
   * the certificate, without share credentials. For every other kind of session the question is an
   * error.
   */
  private SmbCredentials login(CgiTransport fresh, CommandListener listener)
      throws LoginException, IOException {
    try {
      return Login.perform(fresh).orElse(null);
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
