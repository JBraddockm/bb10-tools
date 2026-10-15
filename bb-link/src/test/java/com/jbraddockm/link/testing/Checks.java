package com.jbraddockm.link.testing;

import java.util.Objects;

/** Minimal assertions for the plain-Java checks of this module; no test framework is needed. */
public final class Checks {

  private static int failures;
  private static int total;

  private Checks() {}

  /**
   * Records whether a condition holds.
   *
   * @param condition what must be true
   * @param description what is being checked
   */
  public static void check(boolean condition, String description) {
    total++;
    if (!condition) {
      failures++;
      System.err.println("FAIL: " + description);
    }
  }

  /**
   * Records whether two values are equal.
   *
   * @param expected the expected value
   * @param actual the actual value
   * @param description what is being checked
   */
  public static void equal(Object expected, Object actual, String description) {
    total++;
    if (!Objects.equals(expected, actual)) {
      failures++;
      System.err.println(
          "FAIL: " + description + "\n  expected: " + expected + "\n  actual:   " + actual);
    }
  }

  /**
   * Prints the summary.
   *
   * @param suite the name of the whole run
   * @return the process exit status
   */
  public static int finish(String suite) {
    System.out.println(suite + ": " + (total - failures) + "/" + total + " checks passed");
    return failures == 0 ? 0 : 1;
  }
}
