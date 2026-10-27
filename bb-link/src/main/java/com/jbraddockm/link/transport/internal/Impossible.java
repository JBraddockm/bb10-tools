package com.jbraddockm.link.transport.internal;

/**
 * Runs code that declares a checked exception which cannot happen on a working JVM, such as asking
 * for a message digest that every JDK has, and reports one as a defect of the program.
 */
public final class Impossible {

  /**
   * Code that declares a checked exception.
   *
   * @param <T> what it gives
   * @param <E> the checked exception that it declares
   */
  @FunctionalInterface
  public interface Task<T, E extends Exception> {

    /**
     * Runs the code.
     *
     * @return what it gives
     * @throws E the exception that cannot happen
     */
    T run() throws E;
  }

  private Impossible() {}

  /**
   * Runs the task and gives what it gives. An unchecked exception, or an error, passes through as
   * it is.
   *
   * @param task the code to run
   * @param reason why the checked exception cannot happen, for the message of the defect
   * @param <T> what the task gives
   * @param <E> the checked exception that the task declares
   * @return what the task gives
   * @throws IllegalStateException if the task throws a checked exception, with {@code reason} as
   *     its message and the exception as its cause
   */
  public static <T, E extends Exception> T call(Task<T, E> task, String reason) {
    try {
      return task.run();
    } catch (RuntimeException failure) {
      throw failure;
    } catch (Exception failure) {
      throw new IllegalStateException(reason, failure);
    }
  }
}
