package com.jbraddockm.core.daemon;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A session together with the queue that keeps its commands in the order they arrived.
 *
 * <p>Commands on different workers run concurrently; commands on one worker run one after the
 * other.
 */
final class SessionWorker<S extends ServiceSession> {

  private final String id;
  private final S session;
  private final ExecutorService queue;

  SessionWorker(String id, S session) {
    this.id = id;
    this.session = session;
    this.queue = Executors.newSingleThreadExecutor(DaemonServer.daemonThreads("session-" + id));
  }

  String id() {
    return id;
  }

  S session() {
    return session;
  }

  /** Queues a task behind everything already queued. */
  void submit(Runnable task) {
    queue.execute(task);
  }

  /**
   * Closes the session once everything already queued has finished, then runs {@code afterwards}. A
   * session that fails to close is reported on the error output and does not stop {@code
   * afterwards} from running.
   */
  void closeAfterPending(Runnable afterwards) {
    queue.execute(
        () -> {
          try {
            session.close();
          } catch (RuntimeException failure) {
            System.err.println("session " + id + ": could not close: " + failure.getMessage());
          } finally {
            afterwards.run();
          }
        });
    queue.shutdown();
  }

  /**
   * Abandons queued work without waiting and interrupts the command that is running. The session is
   * not closed here, because closing may wait for that command; the process exit that follows ends
   * it.
   */
  void abandon() {
    queue.shutdownNow();
  }
}
