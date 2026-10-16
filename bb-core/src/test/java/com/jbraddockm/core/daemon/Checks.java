package com.jbraddockm.core.daemon;

import java.util.Objects;

/**
 * Minimal assertions for the plain-Java checks of this module; no test framework is needed. Public
 * so that the checks of other packages of the module can use them.
 */
public final class Checks {

  private static int failures;
  private static int total;

  private Checks() {}

  public static void check(boolean condition, String description) {
    total++;
    if (!condition) {
      failures++;
      System.err.println("FAIL: " + description);
    }
  }

  public static void equal(Object expected, Object actual, String description) {
    total++;
    if (!Objects.equals(expected, actual)) {
      failures++;
      System.err.println(
          "FAIL: " + description + "\n  expected: " + expected + "\n  actual:   " + actual);
    }
  }

  public static int finish(String suite) {
    System.out.println(suite + ": " + (total - failures) + "/" + total + " checks passed");
    return failures == 0 ? 0 : 1;
  }
}
