package com.jbraddockm.core.daemon;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link StandardOutputGuard}. They run in a separate JVM because the guard replaces the
 * standard output of its process.
 */
@DisplayName("StandardOutputGuard: standard output is the protocol and nothing else")
class StandardOutputGuardTest {

  /**
   * Returns where a class was loaded from, so that the child JVM finds it however the tests were
   * started.
   */
  private static String locationOf(Class<?> type) throws URISyntaxException {
    return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
  }

  @Test
  @DisplayName(
      "sends a stray print to the error output, and lets only the claimed stream reach standard"
          + " output")
  void onlyTheClaimedStreamReachesStandardOutput()
      throws IOException, InterruptedException, URISyntaxException {
    var java = System.getProperty("java.home") + "/bin/java";
    var classpath =
        locationOf(StandardOutputGuardProbe.class)
            + File.pathSeparator
            + locationOf(StandardOutputGuard.class);
    var process =
        new ProcessBuilder(java, "-cp", classpath, StandardOutputGuardProbe.class.getName())
            .start();

    var standardOutput =
        new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    var errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

    assertThat(process.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(process.exitValue()).as("the probe ended with: " + errorOutput).isZero();
    assertThat(standardOutput.strip()).isEqualTo("a line of the protocol");
    assertThat(errorOutput)
        .contains("a stray line on standard output")
        .doesNotContain("a line of the protocol");
  }
}
