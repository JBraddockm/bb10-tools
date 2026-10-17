package com.jbraddockm.link.session;

import static com.jbraddockm.link.testing.Checks.check;
import static com.jbraddockm.link.testing.Checks.equal;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.command.Progress;
import com.jbraddockm.core.error.InvalidArgumentException;
import com.jbraddockm.link.properties.DeviceProperties;
import com.jbraddockm.link.properties.GetDeviceProperties;
import com.jbraddockm.link.testing.TestPki;
import com.jbraddockm.link.testing.TlsTestDevice;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;

/**
 * Checks the client certificate: reading the files, offering the certificate to a device that lists
 * a different authority, and the three ways to authenticate.
 */
public final class CertificateChecks {

  private static final String HOST = "127.0.0.1";

  private CertificateChecks() {}

  private static Path write(Path directory, String name, String content) throws IOException {
    return Files.writeString(directory.resolve(name), content);
  }

  private static DeviceSession certificateOnly(TlsTestDevice device, Path certificate, Path key) {
    return DeviceSession.forTarget(
        new DeviceTarget(
            HOST,
            device.port(),
            TransportScheme.HTTPS,
            new Authentication.Certificate(new ClientCertificate(certificate, key))));
  }

  private static String deviceName(CommandOutcome<DeviceProperties> outcome) {
    return outcome instanceof CommandOutcome.Succeeded<DeviceProperties>(var result, _)
        ? result.deviceName()
        : "FAILED: " + outcome;
  }

  /** Collects what a command reports. */
  private static final class Collected implements CommandListener {
    final List<String> warnings = new ArrayList<>();

    @Override
    public void onLog(LogLevel level, String message) {
      if (level == LogLevel.WARN) {
        warnings.add(message);
      }
    }

    @Override
    public void onProgress(Progress progress) {}
  }

  private static CommandOutcome.Failed<?> failed(CommandOutcome<?> outcome) {
    check(outcome instanceof CommandOutcome.Failed<?>, "should have failed: " + outcome);
    return (CommandOutcome.Failed<?>) outcome;
  }

  /**
   * Runs the checks.
   *
   * @throws Exception if the harness itself fails
   */
  public static void run() throws Exception {
    var directory = Files.createTempDirectory("bb-link-certificates");
    var certificate = write(directory, "client_cert.pem", TestPki.LEAF_CERTIFICATE);
    var subject =
        ((X509Certificate)
                CertificateFactory.getInstance("X.509")
                    .generateCertificate(
                        new ByteArrayInputStream(TestPki.LEAF_CERTIFICATE.getBytes())))
            .getSubjectX500Principal()
            .getName();

    // the certificate reaches the device, whichever form the key has, with a login that sends no
    // password, and although the device lists another authority than the one that issued it
    for (var form : List.of("pkcs8", "pkcs1")) {
      var key =
          write(
              directory,
              "key_" + form + ".pem",
              form.equals("pkcs8") ? TestPki.LEAF_KEY_PKCS8 : TestPki.LEAF_KEY_PKCS1);
      try (var device = new TlsTestDevice();
          var session = certificateOnly(device, certificate, key)) {
        var outcome = session.execute(new GetDeviceProperties(), CommandListener.SILENT);
        equal(
            subject, deviceName(outcome), "the device saw the client certificate: key as " + form);
        check(
            device.paths().size() == 2
                && device.paths().getFirst().equals("/cgi-bin/login.cgi?request_version=1")
                && device.paths().getLast().equals("/cgi-bin/dynamicProperties.cgi"),
            "a session with a certificate asks for the login once, without a password, then reads: "
                + form);
        equal(
            "TLS-SHARE",
            session.smbCredentials().orElseThrow().password(),
            "and brings the share credentials: " + form);
        equal(
            List.of(subject, subject),
            device.peers(),
            "the certificate was on both requests: " + form);
      }
    }

    var key = write(directory, "key.pem", TestPki.LEAF_KEY_PKCS8);

    // without a certificate the device sees none
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST, device.port(), TransportScheme.HTTPS, new Authentication.NoPassword()))) {
      equal(
          "none",
          deviceName(session.execute(new GetDeviceProperties(), CommandListener.SILENT)),
          "a password session offers no certificate");
      equal(List.of("none", "none"), device.peers(), "not on the login, not on the command");
    }

    // a certificate and a password: the certificate on every request, and the login as well
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST,
                    device.port(),
                    TransportScheme.HTTPS,
                    new Authentication.CertificateAndPassword(
                        new ClientCertificate(certificate, key), "any password")))) {
      equal(
          subject,
          deviceName(session.execute(new GetDeviceProperties(), CommandListener.SILENT)),
          "certificate and password: the certificate is offered");
      equal(List.of(subject, subject), device.peers(), "on the login and on the command");
      check(device.paths().getFirst().startsWith("/cgi-bin/login.cgi"), "and the login is done");
    }

    // a device that logs a certificate holder in without a cookie, as a real one does: that is a
    // login, with the share credentials, and the certificate carries every later request
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST,
                    device.port(),
                    TransportScheme.HTTPS,
                    new Authentication.CertificateAndPassword(
                        new ClientCertificate(certificate, key), "any password")))) {
      device.issuesLoginCookie = false;
      equal(
          subject,
          deviceName(session.execute(new GetDeviceProperties(), CommandListener.SILENT)),
          "a login without a cookie is accepted when the certificate was offered");
      equal(
          "TLS-SHARE",
          session.smbCredentials().orElseThrow().password(),
          "and brings the share credentials");
      equal(List.of(subject, subject), device.peers(), "the certificate was on every request");
    }
    // without a certificate, a success that brings no cookie is still not a login
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST,
                    device.port(),
                    TransportScheme.HTTPS,
                    new Authentication.Password("x")))) {
      device.issuesLoginCookie = false;
      var error =
          ((CommandOutcome.Failed<?>)
                  failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT)))
              .error();
      equal("AUTH_FAILED", error.code().wireName(), "no certificate and no cookie");
      check(error.message().contains("invalid password"), "is still refused as before");
    }

    // a device that asks for a password at the login although the certificate would do: a warning,
    // no answer to the question, and the session carries on with the certificate alone
    try (var device = new TlsTestDevice();
        var session = certificateOnly(device, certificate, key)) {
      device.behaviour = TlsTestDevice.Behaviour.LOGIN_ASKS_FOR_PASSWORD;
      var collected = new Collected();
      equal(
          subject,
          deviceName(session.execute(new GetDeviceProperties(), collected)),
          "the commands work with the certificate alone");
      check(
          collected.warnings.size() == 1
              && collected.warnings.getFirst().contains("asked for a password"),
          "and the question is reported as a warning: " + collected.warnings);
      check(session.smbCredentials().isEmpty(), "without share credentials");
      check(
          device.paths().stream().noneMatch(path -> path.contains("challenge_data")),
          "and no answer to the challenge was ever sent");
    }

    // a device that does not accept the certificate: the message says so, and nothing is guessed
    try (var device = new TlsTestDevice();
        var session = certificateOnly(device, certificate, key)) {
      device.behaviour = TlsTestDevice.Behaviour.REFUSES_EVERYONE;
      var collected = new Collected();
      var outcome = failed(session.execute(new GetDeviceProperties(), collected));
      equal(
          "AUTH_FAILED",
          ((CommandOutcome.Failed<?>) outcome).error().code().wireName(),
          "a refused certificate");
      check(
          ((CommandOutcome.Failed<?>) outcome)
              .error()
              .message()
              .contains("did not accept the client certificate"),
          "its message names the certificate");
      check(
          device.paths().stream().noneMatch(path -> path.contains("challenge_data")),
          "no password was ever sent");
    }

    // the type none still treats a password question as an error: it never carries on
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST, device.port(), TransportScheme.HTTPS, new Authentication.NoPassword()))) {
      device.behaviour = TlsTestDevice.Behaviour.LOGIN_ASKS_FOR_PASSWORD;
      var error =
          ((CommandOutcome.Failed<?>)
                  failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT)))
              .error();
      equal("PASSWORD_REQUIRED", error.code().wireName(), "none: a password question is an error");
    }

    // a device that ends the handshake for a certificate it does not trust. How the client sees
    // the end of the handshake depends on timing (an alert, or a closed connection), so only the
    // outcome is checked here; the wording is checked on the classifier below.
    try (var device = new TlsTestDevice();
        var session = certificateOnly(device, certificate, key)) {
      device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;
      var error =
          ((CommandOutcome.Failed<?>)
                  failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT)))
              .error();
      check(
          List.of("CONNECTION_FAILED", "CONNECTION_LOST").contains(error.code().wireName()),
          "a certificate refused during the handshake is a connection failure: "
              + error.code().wireName());
    }
    for (var failure :
        List.<IOException>of(
            new SSLPeerUnverifiedException("peer not authenticated"),
            new SSLHandshakeException("Received fatal alert: bad_certificate"))) {
      var offered = FailureClassifier.classify(failure, true);
      equal(
          "CONNECTION_FAILED",
          offered.code().wireName(),
          "a failed handshake with a certificate: " + failure.getMessage());
      check(
          offered.message().contains("client certificate was offered"),
          "the message names the certificate");
      check(offered.message().contains(failure.getMessage()), "and keeps the original message");
      equal("tls", offered.details().get("cause"), "the cause is TLS");
      equal(
          Boolean.TRUE, offered.details().get("certificate"), "and says a certificate was offered");
      var plain = FailureClassifier.classify(failure, false);
      check(
          !plain.message().contains("client certificate was offered"),
          "without a certificate the message does not mention one");
      check(!plain.details().containsKey("certificate"), "and has no certificate detail");
    }
    try (var device = new TlsTestDevice();
        var session =
            DeviceSession.forTarget(
                new DeviceTarget(
                    HOST, device.port(), TransportScheme.HTTPS, new Authentication.NoPassword()))) {
      device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;
      equal(
          "none",
          deviceName(session.execute(new GetDeviceProperties(), CommandListener.SILENT)),
          "a client that presents no certificate is not refused for that");
    }

    // files and keys that cannot be used
    var bad =
        List.of(
            new String[] {"encrypted", TestPki.LEAF_KEY_ENCRYPTED, "is encrypted"},
            new String[] {"ec", TestPki.EC_KEY_SEC1, "openssl pkcs8"},
            new String[] {
              "mismatch", TestPki.OTHER_KEY_PKCS8, "does not belong to the certificate"
            },
            new String[] {"junk", "hello", "contains no private key"});
    for (var entry : bad) {
      var badKey = write(directory, "bad_" + entry[0] + ".pem", entry[1]);
      try (var device = new TlsTestDevice();
          var session = certificateOnly(device, certificate, badKey)) {
        var outcome = failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT));
        var error = ((CommandOutcome.Failed<?>) outcome).error();
        equal(
            "INVALID_ARGUMENT", error.code().wireName(), "a key that cannot be used: " + entry[0]);
        check(
            error.message().contains(entry[2]), "says why: " + entry[0] + " / " + error.message());
        equal("clientKey", error.details().get("param"), "and names the parameter: " + entry[0]);
        equal(List.of(), device.paths(), "nothing was sent: " + entry[0]);
      }
    }
    var junkCertificate = write(directory, "junk_cert.pem", "hello");
    try (var device = new TlsTestDevice();
        var session = certificateOnly(device, junkCertificate, key)) {
      var error =
          ((CommandOutcome.Failed<?>)
                  failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT)))
              .error();
      equal("INVALID_ARGUMENT", error.code().wireName(), "a certificate file that is not one");
      equal("clientCertificate", error.details().get("param"), "names the parameter");
    }
    try (var device = new TlsTestDevice();
        var session = certificateOnly(device, certificate, directory.resolve("missing.pem"))) {
      var error =
          ((CommandOutcome.Failed<?>)
                  failed(session.execute(new GetDeviceProperties(), CommandListener.SILENT)))
              .error();
      equal("IO_ERROR", error.code().wireName(), "a key file that is not there");
    }

    // what an export looks like: text before the blocks, a chain after the leaf
    var exported =
        write(
            directory,
            "exported.pem",
            "Bag Attributes\n    friendlyName: BBCGIAUTH_x\nsubject=/CN=whatever\n"
                + TestPki.LEAF_CERTIFICATE
                + TestPki.LEAF_CERTIFICATE);
    var exportedKey =
        write(
            directory,
            "exported_key.pem",
            "Bag Attributes\n    localKeyID: 01\nKey Attributes: <No Attributes>\n"
                + TestPki.LEAF_KEY_PKCS1);
    var loaded = PemIdentity.load(new ClientCertificate(exported, exportedKey));
    equal(2, loaded.chain().length, "a chain is read, leaf first");
    equal("RSA", loaded.key().getAlgorithm(), "a key after some text is found");

    // the authentication
    var offered = new ClientCertificate(certificate, key);
    var combined = new Authentication.CertificateAndPassword(offered, "hunter2");
    var target = new DeviceTarget(HOST, 443, TransportScheme.HTTPS, combined);
    check(
        !target.toString().contains("hunter2")
            && !target.toString().contains(key.toString())
            && !combined.toString().contains("hunter2")
            && !new Authentication.Password("hunter2").toString().contains("hunter2"),
        "nothing prints the password or the location of the key");
    equal(
        "certificateAndPassword",
        target.authentication().type().wireName(),
        "the type of a combined login");
    check(!combined.certificateAlone(), "a password can be given to the device");
    check(new Authentication.Certificate(offered).certificateAlone(), "a certificate alone cannot");
    check(!new Authentication.NoPassword().certificateAlone(), "no password is not a certificate");
    equal(
        Optional.of("hunter2"),
        combined.passwordForChallenge(),
        "the password that answers a challenge");
    check(
        new Authentication.NoPassword().passwordForChallenge().isEmpty()
            && new Authentication.Certificate(offered).passwordForChallenge().isEmpty(),
        "without a password nothing is ever answered");
    equal(
        Optional.of(offered),
        new Authentication.Certificate(offered).certificateToOffer(),
        "a certificate is offered");
    check(
        new Authentication.Password("x").certificateToOffer().isEmpty(),
        "a password login offers none");
    for (var blank : Arrays.asList("", "  ", null)) {
      check(
          rejects(() -> new Authentication.Password(blank)),
          "a blank password is refused: " + blank);
      check(
          rejects(() -> new Authentication.CertificateAndPassword(offered, blank)),
          "a blank password with a certificate is refused: " + blank);
    }
    for (var type : AuthenticationType.values()) {
      equal(
          type,
          AuthenticationType.of(type.wireName()),
          "a type is found by its wire name: " + type);
    }
    check(rejects(() -> AuthenticationType.of("Password")), "type names are exact");
  }

  private static boolean rejects(Runnable creation) {
    try {
      creation.run();
      return false;
    } catch (InvalidArgumentException _) {
      return true;
    }
  }
}
