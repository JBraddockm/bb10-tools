package com.jbraddockm.device;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * Receives the bytes the deploy tool writes to its output stream and hands them on one complete
 * line at a time, without line terminators.
 */
final class LineCollector extends OutputStream implements AutoCloseable {

  private final Consumer<String> lineHandler;
  private final ByteArrayOutputStream pending = new ByteArrayOutputStream();
  private final PrintStream stream = new PrintStream(this, true, StandardCharsets.UTF_8);
  private boolean closed;

  LineCollector(Consumer<String> lineHandler) {
    this.lineHandler = lineHandler;
  }

  /** The stream to give to the deploy tool. */
  PrintStream stream() {
    return stream;
  }

  @Override
  public void write(int value) {
    switch (value) {
      case '\n' -> emit();
      case '\r' -> {
        // line terminators are not part of a line
      }
      default -> pending.write(value);
    }
  }

  @Override
  public void close() {
    if (closed) {
      return;
    }

    closed = true;
    finish();
  }

  /** Hands on a final line that had no terminating newline. */
  void finish() {
    stream.flush();
    if (pending.size() > 0) {
      emit();
    }
  }

  private void emit() {
    var line = pending.toString(StandardCharsets.UTF_8);
    pending.reset();
    lineHandler.accept(line);
  }
}
