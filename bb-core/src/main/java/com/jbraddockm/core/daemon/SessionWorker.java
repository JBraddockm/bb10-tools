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
   * session is not closed here, because closing may wait for the running command; the process exit
   * that follows ends it.
   */
  void abandon() {
    queue.shutdownNow();
  }
}
