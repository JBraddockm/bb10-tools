package com.jbraddockm.daemon;

import com.jbraddockm.device.CommandListener;
import com.jbraddockm.device.CommandOutcome;
import com.jbraddockm.device.DeviceCommand;
import com.jbraddockm.device.DeviceSession;
import com.jbraddockm.device.DeviceTarget;
import com.jbraddockm.output.CommandResult;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A device session together with the queue that keeps its commands in the order they arrived.
 *
 * <p>Commands on different workers run concurrently; commands on one worker run one after the
 * other.
 */
final class SessionWorker {

  private final String id;
  private final DeviceTarget target;
  private final DeviceSession session;
  private final ExecutorService queue;

  SessionWorker(String id, DeviceTarget target) {
    this.id = id;
    this.target = target;
    this.session = DeviceSession.forTarget(target);
    this.queue = Executors.newSingleThreadExecutor(DaemonServer.daemonThreads("session-" + id));
  }

  String id() {
    return id;
  }

  DeviceTarget target() {
    return target;
  }

  /** Queues a task behind everything already queued. */
  void submit(Runnable task) {
    queue.execute(task);
  }

  <T extends CommandResult> CommandOutcome<T> execute(
      DeviceCommand<T> command, CommandListener listener) {
    return session.execute(command, listener);
  }

  /**
   * Runs {@code afterwards} once everything already queued has finished and the session is closed.
   */
  void closeAfterPending(Runnable afterwards) {
    queue.execute(
        () -> {
          session.close();
          afterwards.run();
        });
    queue.shutdown();
  }

  /**
   * Abandons queued work without waiting. A command that is running is not interrupted and the
   * session is not closed here, because closing waits for the running command; the process exit
   * that follows ends it.
   */
  void abandon() {
    queue.shutdownNow();
  }
}
