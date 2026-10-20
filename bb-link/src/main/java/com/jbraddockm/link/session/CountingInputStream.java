package com.jbraddockm.link.session;

import com.jbraddockm.link.cgi.UploadObserver;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Passes on a file as it is read, and tells an observer how much has gone out. The observer is told
 * about once for each percent of the file, and at least every 64 KiB, so that a large upload does
 * not flood a client with events. Bytes that are skipped count as read; the stream cannot be reset,
 * so the count only moves forward.
 */
final class CountingInputStream extends FilterInputStream {

  private static final long MINIMUM_STEP = 64 * 1024;

  private final UploadObserver observer;
  private final long step;
  private long total;
  private long lastReported;

  CountingInputStream(InputStream source, long length, UploadObserver observer) {
    super(source);
    this.observer = observer;
    this.step = Math.max(MINIMUM_STEP, length / 100);
  }

  @Override
  public int read() throws IOException {
    var value = super.read();
    if (value >= 0) {
      counted(1);
    }
    return value;
  }

  @Override
  public int read(byte[] buffer, int offset, int length) throws IOException {
    var count = super.read(buffer, offset, length);
    if (count > 0) {
      counted(count);
    }
    return count;
  }

  @Override
  public long skip(long count) throws IOException {
    var skipped = super.skip(count);
    if (skipped > 0) {
      counted(skipped);
    }
    return skipped;
  }

  @Override
  public boolean markSupported() {
    return false;
  }

  @Override
  public synchronized void mark(int readLimit) {
    // not supported: the count only moves forward
  }

  @Override
  public synchronized void reset() throws IOException {
    throw new IOException("mark/reset not supported");
  }

  private void counted(long bytes) {
    total += bytes;
    if (total - lastReported >= step) {
      lastReported = total;
      observer.sent(total);
    }
  }
}
