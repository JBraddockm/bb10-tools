package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Keeps the packages that are meant to move to a library of their own independent: {@code pki} may
 * use the JDK and nothing else; {@code store} may use the JDK, {@code pki} and the JSON of the core
 * library. Nothing here may reach into the CGI layer, the sessions or the daemon.
 *
 * <p>The check reads the sources, so it runs from the module folder or from the repository root;
 * where it finds neither, it does not run.
 */
public final class BoundaryChecks {

  private static final String IDENTITY = "com/jbraddockm/link/pairing/";

  private BoundaryChecks() {}

  /**
   * Runs the checks.
   *
   * @throws IOException if a source cannot be read
   */
  public static void run() throws IOException {
    var root =
        Stream.of(Path.of("src/main/java"), Path.of("bb-link/src/main/java"))
            .filter(path -> Files.isDirectory(path.resolve(IDENTITY + "pki")))
            .findFirst();
    if (root.isEmpty()) {
      check(true, "boundary: the sources are not here, so the check did not run");
      return;
    }
    var pki =
        importsOutside(root.get().resolve(IDENTITY + "pki"), List.of("java.", "javax."), "pki");
    equal(List.of(), pki.violations(), "boundary: pki uses the JDK and nothing else");
    check(pki.files() >= 7, "boundary: and the check saw the files of pki: " + pki.files());
    var identity =
        importsOutside(
            root.get().resolve(IDENTITY + "store"),
            List.of(
                "java.",
                "javax.",
                "com.jbraddockm.core.json.",
                "com.jbraddockm.link.pairing.pki.",
                "com.jbraddockm.link.pairing.store."),
            "store");
    equal(
        List.of(), identity.violations(), "boundary: store uses the JDK, pki and the core's JSON");
    check(
        identity.files() >= 4,
        "boundary: and the check saw the files of identity: " + identity.files());
  }

  private record Scan(int files, List<String> violations) {}

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
}
