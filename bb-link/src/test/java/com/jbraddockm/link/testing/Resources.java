package com.jbraddockm.link.testing;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Where tests get their data: text, XML, JSON and PEM do not live in test code but in files under
 * {@code src/test/resources}, in a folder that mirrors the package of the test that uses them.
 *
 * <p>A test in {@code com.jbraddockm.link.pairing.store} reads {@code
 * src/test/resources/com/jbraddockm/link/pairing/store/<name>}. A file that needs a value that only
 * exists at run time (a port, a session token, a folder) holds a placeholder written {@code
 * {{name}}}; {@link #text(Class, String, Map)} fills them in. That is all the construction there
 * is: a placeholder with no value, and a value with no placeholder, are both errors, so a typo
 * cannot pass unnoticed.
 */
public final class Resources {

  private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z0-9_.-]+)}}");

  private Resources() {}

  /**
   * The text of a resource, as it is in the file.
   *
   * @param owner the class whose package names the folder
   * @param name the name of the file in that folder
   * @return the text, read as UTF-8
   * @throws AssertionError if there is no such resource
   */
  public static String text(Class<?> owner, String name) {
    return new String(bytes(owner, name), StandardCharsets.UTF_8);
  }

  /**
   * The text of a resource, with its placeholders filled in.
   *
   * @param owner the class whose package names the folder
   * @param name the name of the file in that folder
   * @param values what each placeholder stands for, by the name inside the braces
   * @return the text
   * @throws IllegalStateException if the file has a placeholder that has no value
   * @throws IllegalArgumentException if a value has no placeholder in the file
   */
  public static String text(Class<?> owner, String name, Map<String, String> values) {
    var unused = new TreeSet<>(values.keySet());
    var filled =
        PLACEHOLDER
            .matcher(text(owner, name))
            .replaceAll(
                found -> {
                  var key = found.group(1);
                  if (!values.containsKey(key)) {
                    throw new IllegalStateException(
                        "the resource "
                            + name
                            + " has the placeholder {{"
                            + key
                            + "}}, with no value");
                  }
                  unused.remove(key);
                  return Matcher.quoteReplacement(values.get(key));
                });
    if (!unused.isEmpty()) {
      throw new IllegalArgumentException(
          "the resource " + name + " has no placeholder for these values: " + unused);
    }
    return filled;
  }

  /**
   * The bytes of a resource.
   *
   * @param owner the class whose package names the folder
   * @param name the name of the file in that folder
   * @return the content
   * @throws AssertionError if there is no such resource
   */
  public static byte[] bytes(Class<?> owner, String name) {
    try (var stream = owner.getResourceAsStream(name)) {
      if (stream == null) {
        throw new AssertionError(
            "no test resource "
                + owner.getPackageName().replace('.', '/')
                + "/"
                + name
                + " (it belongs in src/test/resources)");
      }
      return stream.readAllBytes();
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }

  /**
   * Copies a resource into a folder, for a test that needs the file on disk.
   *
   * @param owner the class whose package names the folder
   * @param name the name of the file in that folder
   * @param folder where to put the copy, which keeps the name
   * @return the copy
   */
  public static Path copyTo(Class<?> owner, String name, Path folder) {
    try {
      return Files.write(folder.resolve(name), bytes(owner, name));
    } catch (IOException failure) {
      throw new UncheckedIOException(failure);
    }
  }
}
