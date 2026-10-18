package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;
import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static com.jbraddockm.link.testing.Sessions.password;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.EcKeys;
import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.store.DeviceId;
import com.jbraddockm.link.pairing.store.FilesExistException;
import com.jbraddockm.link.pairing.store.PairingDirectory;
import com.jbraddockm.link.pairing.store.StoredPairing;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.TestAuthority;
import com.jbraddockm.link.testing.TestFiles;
import com.jbraddockm.link.testing.Warnings;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Checks of the pairing: what is sent, what the device answers and what is kept. The answers of the
 * fake device are those of the real one where they were captured: its own authority as the text of
 * {@code Pairing} and no {@code Status}, and error 7 for anything that is not an insert.
 */
public final class PairingChecks {

  private static final String PIN = "2A3B4C5D";
  private static final String NO_AUTHORITY =
      "<RimTabletResponse><Pairing><Status>Success</Status></Pairing></RimTabletResponse>";
  private static final String REFUSED =
      "<RimTabletResponse><Pairing><Status>Error</Status><Error><ErrorIdentifier>7</ErrorIdentifier><ErrorDescription>Error"
          + " - insert certificate failed.</ErrorDescription></Error>"
          + "</Pairing></RimTabletResponse>";
  private static final String NOT_A_CERTIFICATE =
      "<RimTabletResponse><Pairing><![CDATA[\n"
          + "-----BEGIN CERTIFICATE-----\n"
          + "AAAA\n"
          + "-----END CERTIFICATE-----\n"
          + "]]></Pairing></RimTabletResponse>";

  private PairingChecks() {}

  /**
   * Runs the checks.
   *
   * @throws Exception if a check cannot be run
   */
  public static void run() throws Exception {
    var root = Files.createTempDirectory("bb-link-pairing");
    try {
      pairs(root.resolve("main"));
      answers(root.resolve("answers"));
      guards(root.resolve("guards"));
      directory(root.resolve("directory"));
      identifiers();
      validation(root.resolve("validation"));
    } finally {
      TestFiles.deleteTree(root);
    }
  }

  private static void pairs(Path devices) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var events = new Warnings();
      var result = succeeded(new Pairing().run(session, request(devices, false), events));
      var directory = PairingDirectory.of(devices, PIN);
      equal(PIN, result.pin(), "pair: the PIN of the device");
      check(
          result.deviceId().matches("01[0-9A-F]{32}"),
          "pair: the identifier: 01 and 32 hex digits in capitals: " + result.deviceId());
      check(
          result.commonName().matches("PC issuer [0-9A-F]{16}"),
          "pair: the authority is named as Link's is: " + result.commonName());
      equal(
          4,
          directory.existingFiles().size(),
          "pair: four files: the authority, its key, the device's authority and the pairing file");
      var stored =
          CertificateDetails.of(
              CertificateDetails.firstCertificate(Files.readString(directory.authorityFile())));
      equal(stored, result.authority(), "pair: the file holds what the result describes");
      check(stored.certificateAuthority(), "pair: it is an authority");
      equal(stored.subject(), stored.issuer(), "pair: signed by itself");
      equal(
          "SHA1withECDSA", stored.signatureAlgorithm(), "pair: with ECDSA and SHA-1, as Link's is");
      equal("EC", stored.publicKeyAlgorithm(), "pair: an elliptic-curve key");
      equal(521, stored.publicKeyBits(), "pair: on P-521");
      equal(
          3657L,
          Duration.between(stored.notBefore(), stored.notAfter()).toDays(),
          "pair: ten years, from seven days before");

      var sent = form(device.pairingBodies().getFirst());
      equal(
          List.of("deviceid", "certdata"),
          List.copyOf(sent.keySet()),
          "pair: the two fields that the device reads, and no others");
      equal(result.deviceId(), sent.get("deviceid"), "pair: the identifier that is kept");
      check(
          sent.get("certdata").startsWith("-----BEGIN CERTIFICATE-----"),
          "pair: the authority is sent as PEM");
      equal(
          stored,
          CertificateDetails.of(CertificateDetails.firstCertificate(sent.get("certdata"))),
          "pair: and it is the one that is kept");
      equal(
          sent.get("certdata"),
          device.pairings().get(result.deviceId()),
          "pair: the device keeps what was sent");
      check(
          device.pairingBodies().stream().noneMatch(body -> body.contains("PRIVATE")),
          "pair: the private key is never sent");

      var deviceAuthority =
          CertificateDetails.of(
              CertificateDetails.firstCertificate(
                  Files.readString(directory.deviceAuthorityFile())));
      equal(
          CertificateDetails.of(CertificateDetails.firstCertificate(device.authority.rootPem())),
          deviceAuthority,
          "pair: the device's own authority is kept");
      equal(deviceAuthority, result.deviceAuthority(), "pair: and described");
      keyBelongsToAuthority(directory, "pair: the key file is the authority's key");
      if (Files.getFileStore(devices).supportsFileAttributeView("posix")) {
        equal(
            PosixFilePermissions.fromString("rw-------"),
            Files.getPosixFilePermissions(directory.keyFile()),
            "pair: the key can be read by its owner only");
        equal(
            PosixFilePermissions.fromString("rwx------"),
            Files.getPosixFilePermissions(directory.directory()),
            "pair: and so can the folder");
      }
      var pairing = directory.metadata().orElseThrow();
      equal(result.deviceId(), pairing.deviceId(), "pair: the pairing file has the identifier");
      check(events.warnings().isEmpty(), "pair: nothing to warn of: " + events.warnings());
    }
  }

  private static void answers(Path devices) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var pairing = new Pairing();
      device.pairingAnswer = NO_AUTHORITY;
      var none = failed(pairing.run(session, request(devices.resolve("a"), false), new Warnings()));
      equal(
          "UNEXPECTED_RESPONSE",
          none.error().code().wireName(),
          "answer: a success without the device's authority is not a pairing");
      equal("Success", none.error().details().get("status"), "answer: it says what the status was");
      check(
          String.valueOf(none.error().details().get("answer")).contains("Status"),
          "answer: and shows the answer");
      noFiles(devices.resolve("a"), "answer: nothing is kept");

      device.pairingAnswer = REFUSED;
      var refused =
          failed(pairing.run(session, request(devices.resolve("b"), false), new Warnings()));
      equal(
          "COMMAND_FAILED",
          refused.error().code().wireName(),
          "answer: an error that the device reports");
      equal(
          "Error - insert certificate failed.",
          refused.error().message(),
          "answer: with its own words");
      equal(7, refused.error().details().get("errorIdentifier"), "answer: and its number");
      noFiles(devices.resolve("b"), "answer: nothing is kept");

      device.pairingAnswer = NOT_A_CERTIFICATE;
      var bad = failed(pairing.run(session, request(devices.resolve("c"), false), new Warnings()));
      equal(
          "CERTIFICATE_REJECTED",
          bad.error().code().wireName(),
          "answer: an authority that is not X.509 is rejected");
      noFiles(devices.resolve("c"), "answer: nothing is kept");
    }
    try (var device = new FakeDevice("secret");
        var session = password(device)) {
      var plain =
          failed(new Pairing().run(session, request(devices.resolve("d"), false), new Warnings()));
      equal(
          "COMMAND_FAILED",
          plain.error().code().wireName(),
          "answer: a device that cannot pair says so");
      equal(
          7,
          plain.error().details().get("errorIdentifier"),
          "answer: with error 7, as the real one does");
      noFiles(devices.resolve("d"), "answer: nothing is kept");
    }
  }

  private static void guards(Path devices) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var pairing = new Pairing();
      var first = succeeded(pairing.run(session, request(devices, false), new Warnings()));
      var directory = PairingDirectory.of(devices, PIN);
      var keyBefore = Files.readString(directory.keyFile());
      var again = failed(pairing.run(session, request(devices, false), new Warnings()));
      equal(
          "PAIRING_EXISTS",
          again.error().code().wireName(),
          "guard: a pairing that is there is not replaced without being asked");
      equal(1, device.pairingBodies().size(), "guard: and nothing was sent");
      var second = succeeded(pairing.run(session, request(devices, true), new Warnings()));
      check(
          !first.deviceId().equals(second.deviceId()),
          "guard: replaced, the identifier is a new one");
      check(!keyBefore.equals(Files.readString(directory.keyFile())), "guard: and so is the key");
      equal(
          second.deviceId(),
          directory.metadata().orElseThrow().deviceId(),
          "guard: the file says which pairing is kept");
      equal(
          Set.of(first.deviceId(), second.deviceId()),
          device.pairings().keySet(),
          "guard: the device keeps both, for it cannot be told to forget one");

      var wrong =
          failed(
              pairing.run(
                  session,
                  new PairingRequest("FFFFFFFF", devices.resolve("pin"), false),
                  new Warnings()));
      equal(
          "PIN_MISMATCH",
          wrong.error().code().wireName(),
          "guard: the PIN is confirmed before anything is made");
      equal(2, device.pairingBodies().size(), "guard: and nothing was sent");
    }
  }

  private static void directory(Path devices) throws Exception {
    var keys = EcKeys.generateP521();
    var directory = PairingDirectory.of(devices, PIN);
    check(
        refuses(() -> PairingDirectory.of(devices, "../x")),
        "directory: a PIN is letters and digits");
    try (var _ = directory.prepare(keys, false)) {
      check(
          Files.exists(directory.keyFile().resolveSibling("authority_key.pem.new")),
          "directory: the key waits under a temporary name");
      check(directory.existingFiles().isEmpty(), "directory: and the pairing is not there yet");
    }
    check(
        !Files.exists(directory.keyFile().resolveSibling("authority_key.pem.new")),
        "directory: closed without committing, it is taken back");
    var stored = new StoredPairing("01AB", "PC issuer 1", Instant.parse("2026-10-18T10:00:00Z"));
    try (var pending = directory.prepare(keys, false)) {
      pending.commit("authority", "device", stored);
    }
    equal(
        List.of(
            directory.authorityFile(),
            directory.keyFile(),
            directory.deviceAuthorityFile(),
            directory.metadataFile()),
        directory.existingFiles(),
        "directory: four files once committed");
    equal(Optional.of(stored), directory.metadata(), "directory: the pairing file reads back");
    check(
        !Files.exists(directory.keyFile().resolveSibling("authority_key.pem.new")),
        "directory: no temporary file is left");
    var exists = false;
    try {
      directory.prepare(keys, false).close();
    } catch (FilesExistException expected) {
      exists = expected.getMessage().contains("authority_cert.pem");
    }
    check(exists, "directory: a pairing that is there is not replaced unless that is allowed");
    directory.prepare(keys, true).close();
    equal(
        4,
        directory.existingFiles().size(),
        "directory: replacing and not committing leaves the old pairing as it was");
    equal(
        stored, StoredPairing.fromJsonText(stored.toJsonText()), "directory: the file round-trips");
    check(
        refuses(() -> StoredPairing.fromJsonText("{\"commonName\":\"x\"}")),
        "directory: a file that lacks the identifier");
    check(
        refuses(
            () ->
                StoredPairing.fromJsonText(
                    "{\"deviceId\":\"x\",\"commonName\":\"y\",\"createdAt\":\"yesterday\"}")),
        "directory: a date that is no date");
    check(
        refuses(() -> StoredPairing.fromJsonText("not json")), "directory: text that is not JSON");
  }

  private static void identifiers() {
    var random = new SecureRandom();
    var first = DeviceId.generate(random);
    check(
        first.matches("01[0-9A-F]{32}") && !first.equals(DeviceId.generate(random)),
        "identifier: 01 and 32 hex digits in capitals, and a new one each time");
    equal(
        "AB12",
        DeviceId.normalise(" ab12 "),
        "identifier: written in capitals, without the blanks");
    check(
        refuses(() -> DeviceId.normalise(""))
            && refuses(() -> DeviceId.normalise("a b"))
            && refuses(() -> DeviceId.normalise("x".repeat(65))),
        "identifier: not empty, no spaces, at most 64");
  }

  private static void validation(Path devices) {
    check(
        refuses(() -> new PairingRequest(" ", devices, false), "confirmPin"),
        "validation: a PIN is required");
    check(
        refuses(() -> new PairWithDevice("", "x"), "deviceId"),
        "validation: an identifier is required");
    check(
        refuses(() -> new PairWithDevice("01AB", " "), "authority"),
        "validation: and an authority");
  }

  private static PairingRequest request(Path devices, boolean replace) {
    return new PairingRequest(PIN, devices, replace);
  }

  private static void noFiles(Path devices, String description) throws IOException {
    var directory = PairingDirectory.of(devices, PIN);
    check(
        directory.existingFiles().isEmpty()
            && !Files.exists(directory.keyFile().resolveSibling("authority_key.pem.new")),
        description);
  }

  /** Signs with the key file and verifies with the authority file: they belong together. */
  private static void keyBelongsToAuthority(PairingDirectory directory, String description)
      throws Exception {
    var authority =
        CertificateDetails.firstCertificate(Files.readString(directory.authorityFile()));
    var privateKey =
        KeyFactory.getInstance("EC")
            .generatePrivate(
                new PKCS8EncodedKeySpec(
                    Pem.first(Files.readString(directory.keyFile()), "PRIVATE KEY")
                        .orElseThrow()
                        .content()));
    var data = "proof".getBytes(StandardCharsets.UTF_8);
    var signer = Signature.getInstance("SHA256withECDSA");
    signer.initSign(privateKey);
    signer.update(data);
    var signature = signer.sign();
    var verifier = Signature.getInstance("SHA256withECDSA");
    verifier.initVerify(authority.getPublicKey());
    verifier.update(data);
    check(verifier.verify(signature), description);
  }

  private static Map<String, String> form(String body) {
    var fields = new LinkedHashMap<String, String>();
    for (var pair : body.split("&")) {
      var parts = pair.split("=", 2);
      fields.put(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return fields;
  }

  private static FakeDevice authorityDevice() throws Exception {
    var device = new FakeDevice("secret");
    device.authority = new TestAuthority();
    return device;
  }

  private static boolean refuses(Runnable action) {
    try {
      action.run();
      return false;
    } catch (IllegalArgumentException _) {
      return true;
    }
  }

  private static boolean refuses(Runnable action, String parameter) {
    try {
      action.run();
      return false;
    } catch (InvalidArgumentException invalid) {
      return invalid.parameter().equals(parameter);
    }
  }
}
