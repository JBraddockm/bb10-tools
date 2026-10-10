package com.jbraddockm.device;

import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Lets a running command see what the Deploy tool prints with {@code System.out}.
 *
 * <p>The tool writes most of its output to the stream given to {@code Deploy.setOut}, but some
 * output bypasses it: {@code -devices}, for example, prints the devices it finds with {@code
 * System.out} (only its {@code Info:} lines go through {@code setOut}). Without this class those
 * lines never reach the parser.
 *
 * <p>{@link #install} replaces {@code System.out} with a stream that normally forwards to a
 * fallback (the daemon uses standard error, so stray output cannot corrupt the protocol). While a
 * {@link #capture} is open, everything written to {@code System.out}, from any thread, goes to the
 * capturing command instead. Installing before the tool is loaded matters: a reference to {@code
 * System.out} that the tool keeps from its own initialisation then still points here.
 *
 * <p>{@code System.out} is process-wide, so only one capture can be open at a time; a second one
 * waits. Anything else printed to {@code System.out} during a capture, for example by another
 * session, ends up in the capturing command's output, where its parser reports it as unparsed.
 */
public final class StandardOutputCapture {

  private static final ReentrantLock CAPTURE_LOCK = new ReentrantLock();
  private static volatile Router router;

  private StandardOutputCapture() {}

  /**
   * Replaces {@code System.out}. Calling it again only changes the fallback.
   *
   * @param fallback where {@code System.out} output goes while no capture is open
   */
  public static synchronized void install(PrintStream fallback) {
    if (router == null) {
      router = new Router(fallback);
      System.setOut(new PrintStream(router, true, StandardCharsets.UTF_8));
    } else {
      router.setFallback(fallback);
    }
  }

  /**
   * Sends {@code System.out} output to {@code target} until the returned handle is closed. If
   * {@link #install} was never called, nothing is captured and output goes wherever {@code
   * System.out} already pointed.
   *
   * @param target receives the bytes
   * @return the handle that ends the capture
   */
  static Capture capture(OutputStream target) {
    var installed = router;
    if (installed == null) {
      return () -> {};
    }
    CAPTURE_LOCK.lock();
    installed.setTarget(target);
    return () -> {
      try {
        System.out.flush();
        installed.setTarget(null);
      } finally {
        CAPTURE_LOCK.unlock();
      }
    };
  }

  /** An open capture. Must be closed on the thread that opened it. */
  @FunctionalInterface
  interface Capture extends AutoCloseable {
    @Override
    void close();
  }

  /** The stream behind {@code System.out}: writes to the current target, else the fallback. */
  private static final class Router extends OutputStream {

    private OutputStream fallback;
    private OutputStream target;

    Router(OutputStream fallback) {
      this.fallback = fallback;
    }

    synchronized void setFallback(OutputStream fallback) {
      this.fallback = fallback;
    }

    synchronized void setTarget(OutputStream target) {
      this.target = target;
    }

    private OutputStream current() {
      return target != null ? target : fallback;
    }

    @Override
    public synchronized void write(int value) throws java.io.IOException {
      current().write(value);
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length)
        throws java.io.IOException {
      current().write(bytes, offset, length);
    }

    @Override
    public synchronized void flush() throws java.io.IOException {
      current().flush();
    }
  }
}
