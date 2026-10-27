package com.jbraddockm.link;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keeps the two tiers of bb-link layered. {@code endpoint.*} packages are domains that sit on top of
 * the {@code transport.*} plumbing; they do not reach sideways into one another (save a short,
 * approved allow-list), the plumbing never reaches up into a domain, and nothing but {@link
 * Launcher} depends on the {@code daemon} composition root. The two leaf plumbing packages that
 * could one day move to {@code bb-core} ({@code transport.xml}, {@code transport.internal}) are held
 * to the JDK only. The test reads the sources and is skipped when they are not found.
 *
 * <p>The companion {@code PairingBoundaryTest} holds the tighter line for {@code pairing.pki} and
 * {@code pairing.store}.
 */
@DisplayName("Architecture boundaries: the transport and endpoint tiers stay layered")
class ArchitectureBoundaryTest {

  private static final String ENDPOINT = "com.jbraddockm.link.endpoint.";
  private static final String TRANSPORT = "com.jbraddockm.link.transport.";
  private static final String DAEMON = "com.jbraddockm.link.daemon";

  /**
   * The only endpoint-to-endpoint dependencies that are allowed, as {@code consumer->provider}:
   * pairing reads and confirms the device PIN through {@code actions}; install and uninstall read
   * the installed-package list through {@code properties}. Any other cross-domain edge is a bug.
   */
  private static final Set<String> APPROVED_DOMAIN_EDGES = Set.of("pairing->actions", "update->properties");

  private record JavaFile(String pkg, String name, List<String> imports) {}

  private static Path mainRoot() {
    Optional<Path> root =
        Stream.of(Path.of("src/main/java"), Path.of("bb-link/src/main/java"))
            .filter(path -> Files.isDirectory(path.resolve("com/jbraddockm/link")))
            .findFirst();
    assumeTrue(root.isPresent(), "the sources are not here, so the boundaries were not checked");
    return root.orElseThrow();
  }

  private static List<JavaFile> sources() {
    var link = mainRoot().resolve("com/jbraddockm/link");
    try (var paths = Files.walk(link)) {
      return paths
          .filter(path -> path.toString().endsWith(".java"))
          .filter(path -> !path.getFileName().toString().equals("package-info.java"))
          .map(ArchitectureBoundaryTest::read)
          .toList();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static JavaFile read(Path path) {
    String pkg = "";
    var imports = new java.util.ArrayList<String>();
    try {
      for (var line : Files.readAllLines(path)) {
        var statement = line.strip();
        if (statement.startsWith("package ")) {
          pkg = statement.substring("package ".length(), statement.length() - 1);
        } else if (statement.startsWith("import ") && !statement.startsWith("import static ")) {
          imports.add(statement.substring("import ".length(), statement.length() - 1));
        }
      }
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
    return new JavaFile(pkg, path.getFileName().toString(), imports);
  }

  /** The domain of a package under a tier, e.g. {@code endpoint.pairing.pki} → {@code pairing}. */
  private static String domainUnder(String prefix, String name) {
    return name.substring(prefix.length()).split("\\.", 2)[0];
  }

  @Test
  @DisplayName("a domain reaches another domain only by an approved edge")
  void endpointsDoNotLeakIntoEachOther() {
    var violations =
        sources().stream()
            .filter(file -> file.pkg().startsWith(ENDPOINT))
            .flatMap(
                file -> {
                  var from = domainUnder(ENDPOINT, file.pkg());
                  return file.imports().stream()
                      .filter(imported -> imported.startsWith(ENDPOINT))
                      .map(imported -> from + "->" + domainUnder(ENDPOINT, imported))
                      .filter(edge -> !edge.startsWith(from + "->" + from))
                      .filter(edge -> !APPROVED_DOMAIN_EDGES.contains(edge))
                      .map(edge -> file.name() + ": " + edge);
                })
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("the transport never reaches up into a domain")
  void transportDoesNotImportEndpoint() {
    var violations =
        sources().stream()
            .filter(file -> file.pkg().startsWith(TRANSPORT))
            .flatMap(
                file ->
                    file.imports().stream()
                        .filter(imported -> imported.startsWith(ENDPOINT))
                        .map(imported -> file.name() + " imports " + imported))
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("only the launcher depends on the daemon composition root")
  void nothingBelowImportsTheDaemon() {
    var violations =
        sources().stream()
            .filter(file -> file.pkg().startsWith(ENDPOINT) || file.pkg().startsWith(TRANSPORT))
            .flatMap(
                file ->
                    file.imports().stream()
                        .filter(imported -> imported.startsWith(DAEMON))
                        .map(imported -> file.name() + " imports " + imported))
            .toList();

    assertThat(violations).isEmpty();
  }

  @Test
  @DisplayName("the lift-ready plumbing (xml, internal) stays on the JDK alone")
  void liftReadyPackagesUseTheJdkOnly() {
    var liftReady = Set.of(TRANSPORT + "xml", TRANSPORT + "internal");

    var scanned = sources().stream().filter(file -> liftReady.contains(file.pkg())).toList();
    var violations =
        scanned.stream()
            .flatMap(
                file ->
                    file.imports().stream()
                        .filter(
                            imported ->
                                !imported.startsWith("java.") && !imported.startsWith("javax."))
                        .map(imported -> file.name() + " imports " + imported))
            .toList();

    assertThat(violations).isEmpty();
    assertThat(scanned).as("the lift-ready sources that were read").isNotEmpty();
  }

  /**
   * An absolute classpath resource named in a test — notably a {@code @CsvFileSource(resources =
   * "/…")}. These are written out by hand and do not move when a package is renamed, so a stale one
   * only surfaces on a clean build. This guard catches it at once.
   */
  private static final Pattern ABSOLUTE_RESOURCE =
      Pattern.compile("\"(/com/jbraddockm/[A-Za-z0-9/_.-]+\\.(?:csv|xml|json|pem|txt|bin))\"");

  @Test
  @DisplayName("every absolute classpath resource a test names actually resolves")
  void absoluteTestResourcesResolve() {
    Optional<Path> root =
        Stream.of(Path.of("src/test/java"), Path.of("bb-link/src/test/java"))
            .filter(path -> Files.isDirectory(path.resolve("com/jbraddockm/link")))
            .findFirst();
    assumeTrue(root.isPresent(), "the test sources are not here, so resources were not checked");

    var link = root.orElseThrow().resolve("com/jbraddockm/link");
    try (var paths = Files.walk(link)) {
      var violations =
          paths
              .filter(path -> path.toString().endsWith(".java"))
              .flatMap(
                  path ->
                      ABSOLUTE_RESOURCE.matcher(readString(path)).results().map(match -> match.group(1)))
              .distinct()
              .filter(resource -> getClass().getResource(resource) == null)
              .toList();

      assertThat(violations).isEmpty();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  private static String readString(Path path) {
    try {
      return Files.readString(path);
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
