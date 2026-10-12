package com.qnx.bbt.packager;

import java.io.PrintStream;

public class DefaultPackagerOutput implements IPackagerOutput {
  private PrintStream out;
  private PrintStream err;
  private boolean trace;
  private boolean verbose;

  public DefaultPackagerOutput() {
    this(System.out, System.err);
  }

  public DefaultPackagerOutput(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
    this.verbose = false;
    this.trace = false;
  }

  /**
   * SecurityManager-based System.exit interception is not available on Java 25. This method remains
   * for binary/source compatibility.
   */
  public static void interceptSystemExit() {
    // Intentionally empty.
  }

  public void setTrace(boolean trace) {
    this.trace = trace;
  }

  public boolean isTraceOn() {
    return this.trace;
  }

  public void setVerbose(boolean verbose) {
    this.verbose = verbose;
  }

  public void println(String string) {
    this.out.println(string);
  }

  public void printInfo(String message) {
    if (this.verbose) {
      this.err.println(Messages.getString("Pack.Info") + message);
    }
  }

  public void printWarning(String message) {
    this.err.println(Messages.getString("Pack.Warning") + message);
  }

  public void printFatalError(Throwable e) {
    this.err.println(Messages.getString("Pack.Error") + e.getLocalizedMessage());
    this.callSystemExit();
  }

  protected void callSystemExit() {
    System.exit(1);
  }

  public void printTrace(String msg) {
    if (this.trace) {
      this.err.println(Messages.getString("Pack.Info") + msg);
    }
  }

  public void printSuccess(String msg) {
    this.out.println(Messages.getString("Pack.Info") + msg);
  }

  public PrintStream getOutputStream() {
    return this.out;
  }

  protected void setOutputStream(PrintStream out) {
    this.out = out;
  }

  public PrintStream getErrorStream() {
    return this.err;
  }

  protected void setErrorStream(PrintStream err) {
    this.err = err;
  }
}
