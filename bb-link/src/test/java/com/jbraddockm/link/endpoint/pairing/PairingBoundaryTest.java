package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies that the packages {@code pki} and {@code store} depend only on the JDK and on each
 * other, so that they can move to a library of their own. The test reads the sources and is skipped
 * when they are not found.
 */
@DisplayName("The boundary of pairing: pki and store depend on little")
class PairingBoundaryTest {

  private static final String PAIRING = "com/jbraddockm/link/endpoint/pairing/";

  private record Scan(int files, List<String> violations) {}

  private static Path sources() {
    Optional<Path> root =
        Stream.of(Path.of("src/main/java"), Path.of("bb-link/src/main/java"))
            .filter(path -> Files.isDirectory(path.resolve(PAIRING + "pki")))
            .findFirst();
    assumeTrue(root.isPresent(), "the sources are not here, so the boundary was not checked");
    return root.orElseThrow();
  }

  private static Scan importsOutside(Path folder, List<String> allowed, String name)
      throws IOException {
    var violations = new ArrayList<String>();
    var files = 0;
    try (var sources = Files.list(folder)) {
      for (var source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
        files++;
        for (var line : Files.readAllLines(source)) {
          var statement = line.strip();
          if (statement.startsWith("import ") && !statement.startsWith("import static ")) {
            var imported = statement.substring("import ".length(), statement.length() - 1);
            if (allowed.stream().noneMatch(imported::startsWith)) {
              violations.add(name + "/" + source.getFileName() + " imports " + imported);
            }
          }
        }
      }
    }
    return new Scan(files, violations);
  }

  @Test
  @DisplayName("pki uses the JDK and nothing else, and the test saw its files")
  void pkiUsesTheJdkOnly() throws IOException {
    var scan =
        importsOutside(sources().resolve(PAIRING + "pki"), List.of("java.", "javax."), "pki");

    assertThat(scan.violations()).isEmpty();
    assertThat(scan.files()).as("the files of pki that were read").isGreaterThanOrEqualTo(7);
  }

  @Test
  @DisplayName("store uses the JDK, pki and the JSON of the core, and the test saw its files")
  void storeUsesTheJdkPkiAndJson() throws IOException {
    var allowed =
        List.of(
            "java.",
            "javax.",
            "com.jbraddockm.core.json.",
            "com.jbraddockm.link.endpoint.pairing.pki.",
            "com.jbraddockm.link.endpoint.pairing.store.");

    var scan = importsOutside(sources().resolve(PAIRING + "store"), allowed, "store");

    assertThat(scan.violations()).isEmpty();
    assertThat(scan.files()).as("the files of store that were read").isGreaterThanOrEqualTo(4);
  }
}
