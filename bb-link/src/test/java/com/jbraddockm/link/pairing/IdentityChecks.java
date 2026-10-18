package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.pki.RsaKeys;
import com.jbraddockm.link.pairing.store.FilesExistException;
import com.jbraddockm.link.pairing.store.IdentityDirectory;
import com.jbraddockm.link.pairing.store.StoredIdentity;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/** Checks the identity on disk: the identifier, the identity file, and the two-step write. */
public final class IdentityChecks {

  private static final String CERTIFICATE =
      "-----BEGIN CERTIFICATE-----\nAAAA\n-----END CERTIFICATE-----\n";

  private IdentityChecks() {}

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    storedIdentity();
    var root = Files.createTempDirectory("bb-link-identity");
    try {
      directory(root.resolve("devices"));
    } finally {
      deleteTree(root);
    }
  }

  private static boolean refuses(Supplier<?> action) {
    try {
      action.get();
      return false;
    } catch (IllegalArgumentException _) {
      return true;
    }
  }

  private static void storedIdentity() {
    var identity = new StoredIdentity("BBCGIAUTH [1] x", Instant.parse("2026-10-17T10:00:00Z"));
    equal(
        identity,
        StoredIdentity.fromJsonText(identity.toJsonText()),
        "identity file: read back as it was written");
    check(identity.toJsonText().endsWith("\n"), "identity file: ends with a line break");
    check(refuses(() -> StoredIdentity.fromJsonText("{")), "identity file: not JSON");
    check(
        refuses(() -> StoredIdentity.fromJsonText("{\"commonName\":\"x\"}")),
        "identity file: a field is missing");
    check(
        refuses(
            () ->
                StoredIdentity.fromJsonText("{\"commonName\":\"y\",\"createdAt\":\"yesterday\"}")),
        "identity file: a date that is not a date");
    equal(
        identity,
        StoredIdentity.fromJsonText(
            "{\"deviceId\":\"01AB\",\"commonName\":\"BBCGIAUTH [1] x\","
                + "\"createdAt\":\"2026-10-17T10:00:00Z\"}"),
        "identity file: one that an earlier version wrote, with an identifier, is still read");
  }

  private static void directory(Path devices) throws Exception {
    check(refuses(() -> IdentityDirectory.of(devices, "../x")), "directory: a PIN is no path");
    check(refuses(() -> IdentityDirectory.of(devices, "")), "directory: a PIN is not empty");
    var identity = IdentityDirectory.of(devices, "2BD63247");
    equal(devices.resolve("2BD63247"), identity.directory(), "directory: a folder for each PIN");
    equal(
        "client_cert.pem",
        identity.certificateFile().getFileName().toString(),
        "directory: the certificate's name");
    equal(
        "client_key.pem", identity.keyFile().getFileName().toString(), "directory: the key's name");
    equal(List.of(), identity.existingFiles(), "directory: nothing there yet");
    check(identity.metadata().isEmpty(), "directory: no identity file yet");

    var keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var stored = new StoredIdentity("BBCGIAUTH [1] one", Instant.parse("2026-10-17T10:00:00Z"));
    try (var pending = identity.prepare(keys, stored, false)) {
      check(
          Files.exists(identity.keyFile().resolveSibling("client_key.pem.new")),
          "write: the key waits under another name");
      check(!Files.exists(identity.keyFile()), "write: and is not in its place yet");
      pending.commit(CERTIFICATE);
    }
    equal(
        List.of(identity.certificateFile(), identity.keyFile(), identity.metadataFile()),
        identity.existingFiles(),
        "write: the three files are there");
    check(
        Stream.of(identity.directory().toFile().list()).noneMatch(name -> name.endsWith(".new")),
        "write: nothing is left under the temporary name");
    equal(
        CERTIFICATE,
        Files.readString(identity.certificateFile()),
        "write: the certificate as given");
    equal(stored, identity.metadata().orElseThrow(), "write: the identity file");
    equal(
        HexFormat.of().formatHex(keys.getPrivate().getEncoded()),
        HexFormat.of()
            .formatHex(
                KeyFactory.getInstance("RSA")
                    .generatePrivate(
                        new PKCS8EncodedKeySpec(
                            Pem.first(Files.readString(identity.keyFile()), "PRIVATE KEY")
                                .orElseThrow()
                                .content()))
                    .getEncoded()),
        "write: the key file holds the key, as PKCS #8");
    if (FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
      equal(
          "rw-------",
          PosixFilePermissions.toString(Files.getPosixFilePermissions(identity.keyFile())),
          "write: the key is for its owner only");
      equal(
          "rwx------",
          PosixFilePermissions.toString(Files.getPosixFilePermissions(identity.directory())),
          "write: the folder too");
    }

    var other = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
    var replacement =
        new StoredIdentity("BBCGIAUTH [1] two", Instant.parse("2026-10-18T10:00:00Z"));
    var existing = new boolean[1];
    try {
      identity.prepare(other, replacement, false);
    } catch (FilesExistException exists) {
      existing[0] = true;
      check(
          exists.getMessage().contains("client_key.pem"),
          "write: the message names the files: " + exists.getMessage());
      equal(identity.directory().toString(), exists.directory(), "write: and the folder");
    }
    check(existing[0], "write: an identity that is there is not written over");
    check(
        !Files.exists(identity.keyFile().resolveSibling("client_key.pem.new")),
        "write: and nothing was written");

    var keyBefore = Files.readString(identity.keyFile());
    try (var pending = identity.prepare(other, replacement, true)) {
      check(pending != null, "replace: allowed when asked");
      // closed without a commit: the identity that works stays as it was
    }
    equal(
        keyBefore,
        Files.readString(identity.keyFile()),
        "replace: abandoned, the old key is as it was");
    equal(
        CERTIFICATE,
        Files.readString(identity.certificateFile()),
        "replace: and the old certificate");
    equal(stored, identity.metadata().orElseThrow(), "replace: and the old identity file");
    check(
        Stream.of(identity.directory().toFile().list()).noneMatch(name -> name.endsWith(".new")),
        "replace: the temporary files are taken back");
    try (var pending = identity.prepare(other, replacement, true)) {
      pending.commit(CERTIFICATE.replace("AAAA", "BBBB"));
    }
    check(
        !keyBefore.equals(Files.readString(identity.keyFile())),
        "replace: committed, the key is the new one");
    equal(replacement, identity.metadata().orElseThrow(), "replace: and so is the identity file");

    Files.writeString(identity.metadataFile(), "{ not json");
    var unreadable = new boolean[1];
    try {
      identity.metadata();
    } catch (IllegalArgumentException _) {
      unreadable[0] = true;
    }
    check(unreadable[0], "directory: an identity file that is not readable says so");
  }

  private static void deleteTree(Path root) throws IOException {
    if (!Files.exists(root)) {
      return;
    }
    try (var paths = Files.walk(root)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    }
  }
}
