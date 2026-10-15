package com.jbraddockm.core.daemon;

import java.util.Objects;

/** Minimal assertions for the plain-Java checks of this module; no test framework is needed. */
final class Checks {

  private static int failures;
  private static int total;

  private Checks() {}

  static void check(boolean condition, String description) {
    total++;
    if (!condition) {
      failures++;
      System.err.println("FAIL: " + description);
    }
  }

  static void equal(Object expected, Object actual, String description) {
    total++;
    if (!Objects.equals(expected, actual)) {
      failures++;
      System.err.println(
          "FAIL: " + description + "\n  expected: " + expected + "\n  actual:   " + actual);
    }
  }

  static int finish(String suite) {
    System.out.println(suite + ": " + (total - failures) + "/" + total + " checks passed");
    return failures == 0 ? 0 : 1;
  }
}
