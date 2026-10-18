package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;
import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static com.jbraddockm.link.testing.Sessions.password;

import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.store.IdentityDirectory;
import com.jbraddockm.link.testing.FakeDevice;
import com.jbraddockm.link.testing.TestAuthority;
import com.jbraddockm.link.testing.TestDer;
import com.jbraddockm.link.testing.TestFiles;
import com.jbraddockm.link.testing.TlsTestDevice;
import com.jbraddockm.link.testing.Warnings;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Checks of the certificate that the device is asked to sign through {@code login.cgi}: the request
 * that goes out, what is done with each kind of answer, and what is kept. The answers of the fake
 * device are those of the real one where they were captured: the certificate as PEM and nothing
 * else, and an error 260 in {@code CgiAuth} for a body of another content type. The other errors
 * are made up.
 */
public final class CertificateRetrievalChecks {

  private static final String PIN = "2A3B4C5D";
  private static final String UNREAD =
      "<RimTabletResponse><CgiAuth><Status>Success</Status></CgiAuth></RimTabletResponse>";
  private static final String WRONG_CONTENT_TYPE =
      "<RimTabletResponse><CgiAuth><Status>Error</Status><Error><ErrorIdentifier>260</ErrorIdentifier><ErrorDescription>Expected"
          + " ContentType of application/octet-stream</ErrorDescription>"
          + "</Error></CgiAuth></RimTabletResponse>";

  private CertificateRetrievalChecks() {}

  /**
   * Runs the checks.
   *
   * @throws Exception if a check cannot be run
   */
  public static void run() throws Exception {
    var root = Files.createTempDirectory("bb-link-issuance");
    try (var tls = new TlsTestDevice()) {
      issues(root.resolve("main"), tls);
      answers(root.resolve("answers"));
      guards(root.resolve("guards"));
      validation(root.resolve("validation"));
    } finally {
      TestFiles.deleteTree(root);
    }
  }

  private static void issues(Path devices, TlsTestDevice tls) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var events = new Warnings();
      var result =
          succeeded(
              new CertificateRetrieval()
                  .run(session, request(devices, "workstation", false, true, tls.port()), events));
      var identity = IdentityDirectory.of(devices, PIN);
      equal(PIN, result.pin(), "certificate: the PIN of the device");
      equal(
          "BBCGIAUTH [" + PIN + "] workstation",
          result.commonName(),
          "certificate: the common name as the Windows client writes it: the PIN in brackets, then"
              + " the host");
      equal(
          3,
          identity.existingFiles().size(),
          "certificate: three files: the certificate, the key and the identity file");
      var stored =
          CertificateDetails.of(
              CertificateDetails.firstCertificate(Files.readString(identity.certificateFile())));
      equal(stored, result.certificate(), "certificate: the file holds what the result describes");
      check(
          !stored.certificateAuthority(),
          "certificate: a certificate for a client, not an authority");
      check(
          stored.subject().contains("BBCGIAUTH"), "certificate: the subject: " + stored.subject());
      equal(2048, stored.publicKeyBits(), "certificate: an RSA key of 2048 bits");
      keyBelongsToCertificate(identity, "certificate: the key file is the certificate's key");
      var file = Files.readString(identity.metadataFile());
      check(
          file.contains("commonName") && !file.contains("deviceId"),
          "certificate: the identity file has the name and no identifier of the device: "
              + file.strip());

      var seen = device.authenticationRequests();
      equal(1, seen.size(), "certificate: one request reached login.cgi");
      equal(
          "request_version=3&cgiauth=1",
          seen.getFirst().query(),
          "certificate: with the two parameters");
      equal(
          "application/octet-stream",
          seen.getFirst().contentType(),
          "certificate: as the device requires");
      var text = seen.getFirst().body();
      check(
          text.startsWith("-----BEGIN CERTIFICATE REQUEST-----"),
          "certificate: the request is the body, unchanged");
      var information =
          TestDer.read(Pem.first(text, "CERTIFICATE REQUEST").orElseThrow().content(), 0);
      var entries =
          information.children().get(0).children().get(1).children().stream()
              .map(set -> set.children().getFirst().children())
              .toList();
      equal(1, entries.size(), "certificate: a subject of one entry, and no description");
      equal("550403", hex(entries.getFirst().get(0).content()), "certificate: the common name");
      equal(
          "BBCGIAUTH [" + PIN + "] workstation",
          utf8(entries.getFirst().get(1).content()),
          "certificate: as the Windows client's own request has it");
      equal(
          "2a864886f70d010105",
          hex(information.children().get(1).children().getFirst().content()),
          "certificate: signed with SHA-1 and RSA");
      check(
          device.authenticationRequests().stream()
              .noneMatch(request -> request.body().contains("PRIVATE")),
          "certificate: the private key is never sent");
      equal(
          Verification.State.VERIFIED,
          result.verification().state(),
          "certificate: the device took the certificate for a login");
      check(
          tls.peers().stream()
              .anyMatch(peer -> peer.contains("BBCGIAUTH [" + PIN + "] workstation")),
          "certificate: the trial presented it: " + tls.peers());
      check(events.warnings().isEmpty(), "certificate: nothing to warn of: " + events.warnings());
    }
  }

  private static void answers(Path devices) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var issuance = new CertificateRetrieval();
      device.authenticationAnswer = UNREAD;
      var none =
          failed(
              issuance.run(
                  session,
                  request(devices.resolve("a"), "workstation", false, false, 8443),
                  new Warnings()));
      equal(
          "UNEXPECTED_RESPONSE",
          none.error().code().wireName(),
          "answer: a success without a certificate is not a success");
      equal("Success", none.error().details().get("status"), "answer: it says what the status was");
      check(
          String.valueOf(none.error().details().get("answer")).contains("Status"),
          "answer: and shows the answer: " + none.error().details().get("answer"));
      equal(
          List.of(),
          IdentityDirectory.of(devices.resolve("a"), PIN).existingFiles(),
          "answer: nothing is kept");

      device.authenticationAnswer = "hello";
      var text =
          failed(
              issuance.run(
                  session,
                  request(devices.resolve("b"), "workstation", false, false, 8443),
                  new Warnings()));
      equal(
          "UNEXPECTED_RESPONSE",
          text.error().code().wireName(),
          "answer: a text that is no XML either");
      check(
          String.valueOf(text.error().details().get("answer")).contains("hello"),
          "answer: is shown: " + text.error().details());

      device.authenticationAnswer = WRONG_CONTENT_TYPE;
      var error =
          failed(
              issuance.run(
                  session,
                  request(devices.resolve("c"), "workstation", false, false, 8443),
                  new Warnings()));
      equal(
          "COMMAND_FAILED",
          error.error().code().wireName(),
          "answer: the error of the real device for another content type");
      equal(
          "Expected ContentType of application/octet-stream",
          error.error().message(),
          "answer: with its own words");
      equal(260, error.error().details().get("errorIdentifier"), "answer: and its number");
      equal(
          List.of(),
          IdentityDirectory.of(devices.resolve("c"), PIN).existingFiles(),
          "answer: and nothing is kept");

      device.authenticationAnswer = null;
      device.authenticationForAnotherKey = true;
      var other =
          failed(
              issuance.run(
                  session,
                  request(devices.resolve("d"), "workstation", false, false, 8443),
                  new Warnings()));
      equal(
          "CERTIFICATE_REJECTED",
          other.error().code().wireName(),
          "answer: a certificate for another key is rejected");
      equal(
          List.of(),
          IdentityDirectory.of(devices.resolve("d"), PIN).existingFiles(),
          "answer: and nothing is kept");
    }
    try (var device = new FakeDevice("secret");
        var session = password(device)) {
      var plain =
          failed(
              new CertificateRetrieval()
                  .run(
                      session,
                      request(devices.resolve("e"), "workstation", false, false, 8443),
                      new Warnings()));
      equal(
          "COMMAND_FAILED",
          plain.error().code().wireName(),
          "answer: a device with no authority says that it cannot");
    }
  }

  private static void guards(Path devices) throws Exception {
    try (var device = authorityDevice();
        var session = password(device)) {
      var issuance = new CertificateRetrieval();
      var first =
          succeeded(
              issuance.run(
                  session, request(devices, "workstation", false, false, 8443), new Warnings()));
      equal(
          Verification.State.SKIPPED,
          first.verification().state(),
          "guard: not tried when switched off");
      var identity = IdentityDirectory.of(devices, PIN);
      var keyBefore = Files.readString(identity.keyFile());
      var again =
          failed(
              issuance.run(
                  session, request(devices, "workstation", false, false, 8443), new Warnings()));
      equal(
          "IDENTITY_EXISTS",
          again.error().code().wireName(),
          "guard: an identity that is there is not replaced without being asked");
      equal(1, device.authenticationRequests().size(), "guard: and nothing was sent");
      succeeded(
          issuance.run(
              session, request(devices, "workstation", true, false, 8443), new Warnings()));
      check(
          !keyBefore.equals(Files.readString(identity.keyFile())),
          "guard: replaced, the key is a new one");
      equal(2, device.authenticationRequests().size(), "guard: one request more");

      var wrong =
          failed(
              issuance.run(
                  session,
                  new CertificateRetrievalRequest(
                      "FFFFFFFF", devices.resolve("pin"), Optional.empty(), false, false, 8443),
                  new Warnings()));
      equal(
          "PIN_MISMATCH",
          wrong.error().code().wireName(),
          "guard: the PIN is confirmed before anything is made");
      equal(2, device.authenticationRequests().size(), "guard: and nothing was sent");

      var named =
          succeeded(
              issuance.run(
                  session,
                  request(devices.resolve("long"), "h".repeat(30), false, false, 8443),
                  new Warnings()));
      equal(
          "BBCGIAUTH [" + PIN + "] " + "h".repeat(19),
          named.commonName(),
          "guard: a long name is cut to 19 characters");
      var local =
          succeeded(
              issuance.run(
                  session,
                  new CertificateRetrievalRequest(
                      PIN, devices.resolve("local"), Optional.empty(), false, false, 8443),
                  new Warnings()));
      check(
          local.commonName().matches("BBCGIAUTH \\[" + PIN + "\\] \\S+"),
          "guard: this computer's name when none is given: " + local.commonName());
    }

    // an identity file that an earlier version wrote, with an identifier, is replaced by one
    // without
    try (var device = authorityDevice();
        var session = password(device)) {
      var identity = IdentityDirectory.of(devices.resolve("old"), PIN);
      Files.createDirectories(identity.directory());
      Files.writeString(identity.certificateFile(), "old");
      Files.writeString(identity.keyFile(), "old");
      Files.writeString(
          identity.metadataFile(),
          "{\"deviceId\":\"01AB\",\"commonName\":\"PC issuer"
              + " 1\",\"createdAt\":\"2026-10-17T10:00:00Z\"}");
      succeeded(
          new CertificateRetrieval()
              .run(
                  session,
                  request(devices.resolve("old"), "workstation", true, false, 8443),
                  new Warnings()));
      check(
          !Files.readString(identity.metadataFile()).contains("deviceId"),
          "guard: the old identity file is replaced");
    }
  }

  private static void validation(Path devices) {
    check(
        refuses(
            () ->
                new CertificateRetrievalRequest(" ", devices, Optional.empty(), false, false, 8443),
            "confirmPin"),
        "validation: a PIN is required");
    check(
        refuses(
            () -> new CertificateRetrievalRequest(PIN, devices, Optional.empty(), false, false, 0),
            "verifyPort"),
        "validation: a port");
    check(
        refuses(
            () ->
                new CertificateRetrievalRequest(PIN, devices, Optional.of(" "), false, false, 8443),
            "hostName"),
        "validation: a name is not blank");
    check(
        refuses(() -> new GetAuthenticationCertificate(" "), "csr"),
        "validation: a request is required");
  }

  private static CertificateRetrievalRequest request(
      Path devices, String host, boolean replace, boolean verify, int port) {
    return new CertificateRetrievalRequest(PIN, devices, Optional.of(host), replace, verify, port);
  }

  /** Signs with the key file and verifies with the certificate file: they belong together. */
  private static void keyBelongsToCertificate(IdentityDirectory identity, String description)
      throws Exception {
    var certificate =
        CertificateDetails.firstCertificate(Files.readString(identity.certificateFile()));
    var privateKey =
        KeyFactory.getInstance("RSA")
            .generatePrivate(
                new PKCS8EncodedKeySpec(
                    Pem.first(Files.readString(identity.keyFile()), "PRIVATE KEY")
                        .orElseThrow()
                        .content()));
    var data = "proof".getBytes(StandardCharsets.UTF_8);
    var signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update(data);
    var signature = signer.sign();
    var verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(certificate.getPublicKey());
    verifier.update(data);
    check(verifier.verify(signature), description);
  }

  private static FakeDevice authorityDevice() throws Exception {
    var device = new FakeDevice("secret");
    device.authority = new TestAuthority();
    return device;
  }

  private static boolean refuses(Runnable action, String parameter) {
    try {
      action.run();
      return false;
    } catch (InvalidArgumentException invalid) {
      return invalid.parameter().equals(parameter);
    }
  }

  private static String hex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  private static String utf8(byte[] bytes) {
    return new String(bytes, StandardCharsets.UTF_8);
  }
}
