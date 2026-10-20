package com.jbraddockm.link.pairing;

import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.link.pairing.pki.CertificateDetails;
import com.jbraddockm.link.pairing.pki.Pem;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import com.jbraddockm.link.pairing.store.IdentityDirectory;
import com.jbraddockm.link.pairing.store.StoredIdentity;
import com.jbraddockm.link.session.ClientCertificate;
import com.jbraddockm.link.testing.DeviceFixture;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.TestDer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CertificateRetrieval}: what is stored and sent when the device signs a
 * certificate. Clock and verification are replaced by test doubles.
 */
@DisplayName("CertificateRetrieval: a certificate that the device signs")
class CertificateRetrievalTest {

  private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");
  private static final String COMMON_NAME = "BBCGIAUTH [2A3B4C5D] workstation";

  private record Trial(ClientCertificate identity, int port, String pin, boolean filesExisted) {}

  @TempDir private Path devices;
  private DeviceFixture fixture;
  private Logs logs;
  private List<Trial> trials;
  private CertificateRetrievalResult result;
  private IdentityDirectory directory;

  private Verification trialOutcome = Verification.verified();

  private CertificateRetrieval retrieval() {
    return new CertificateRetrieval(
        (_, identity, port, pin, _) -> {
          trials.add(
              new Trial(
                  identity,
                  port,
                  pin,
                  Files.exists(identity.certificateFile()) && Files.exists(identity.keyFile())));
          return trialOutcome;
        },
        Clock.fixed(NOW, ZoneOffset.UTC));
  }

  private CertificateRetrievalResult retrieve(boolean verify) {
    return succeeded(
        retrieval()
            .run(
                fixture.session(),
                new CertificateRetrievalRequest(
                    "2A3B4C5D", devices, Optional.of("workstation"), false, verify, 8443),
                logs));
  }

  @BeforeEach
  void openTheDevice() {
    fixture = DeviceFixture.withAuthority();
    logs = new Logs();
    trials = new ArrayList<>();
    directory = IdentityDirectory.of(devices, "2A3B4C5D");
  }

  @AfterEach
  void closeTheDevice() {
    fixture.close();
  }

  @Nested
  @DisplayName("a certificate that is signed and tried")
  class Signed {

    @BeforeEach
    void retrieveIt() {
      result = retrieve(true);
    }

    @Test
    @DisplayName(
        "gives the PIN, the common name, the three files, and the time that the days are counted"
            + " from")
    void givesTheIdentity() {
      assertThat(result.pin()).isEqualTo("2A3B4C5D");
      assertThat(result.commonName()).isEqualTo(COMMON_NAME);
      assertThat(result.directory()).isEqualTo(devices.resolve("2A3B4C5D"));
      assertThat(result.certificateFile()).isEqualTo(directory.certificateFile());
      assertThat(result.keyFile()).isEqualTo(directory.keyFile());
      assertThat(result.issuedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
        "keeps three files: the certificate, the key, and the identity file with the name and the"
            + " time")
    void keepsThreeFiles() throws Exception {
      assertThat(directory.existingFiles())
          .containsExactly(
              directory.certificateFile(), directory.keyFile(), directory.metadataFile());
      assertThat(directory.metadataFile())
          .hasContent(new StoredIdentity(COMMON_NAME, NOW).toJsonText());
    }

    @Test
    @DisplayName(
        "keeps the certificate that the device signed, as a certificate for a client and not an"
            + " authority")
    void keepsTheCertificate() throws Exception {
      var details =
          CertificateDetails.of(
              CertificateDetails.firstCertificate(Files.readString(directory.certificateFile())));

      assertThat(details).isEqualTo(result.certificate());
      assertThat(details.certificateAuthority()).isFalse();
      assertThat(details.subject()).contains(COMMON_NAME);
      assertThat(details.publicKeyAlgorithm()).isEqualTo("RSA");
      assertThat(details.publicKeyBits()).isEqualTo(2048);
    }

    @Test
    @DisplayName("keeps a private key that is the key of the certificate")
    void theKeyIsTheKeyOfTheCertificate() throws Exception {
      var pkcs8 =
          Pem.first(Files.readString(directory.keyFile()), "PRIVATE KEY").orElseThrow().content();
      var privateKey =
          (RSAPrivateCrtKey)
              KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      var certificate =
          CertificateDetails.firstCertificate(Files.readString(directory.certificateFile()));

      assertThat(((RSAPublicKey) certificate.getPublicKey()).getModulus())
          .isEqualTo(privateKey.getModulus());
      assertThat(privateKey.getModulus().bitLength()).isEqualTo(2048);
    }

    @Test
    @DisplayName(
        "tries the certificate once, after it is kept, on the port asked for, with the PIN of the"
            + " device")
    void triesTheCertificate() {
      assertThat(trials).hasSize(1);
      var trial = trials.getFirst();
      assertThat(trial.filesExisted())
          .as("the files are there when the certificate is tried")
          .isTrue();
      assertThat(trial.identity())
          .isEqualTo(new ClientCertificate(directory.certificateFile(), directory.keyFile()));
      assertThat(trial.port()).isEqualTo(8443);
      assertThat(trial.pin()).isEqualTo("2A3B4C5D");
      assertThat(result.verification()).isEqualTo(Verification.verified());
    }

    @Test
    @DisplayName(
        "logs what it does at the level of information, in order, and has nothing to warn of")
    void logsWhatItDoes() {
      assertThat(logs.messages(LogLevel.INFO))
          .containsSubsequence(
              "Asking the device to sign a certificate for " + COMMON_NAME,
              "Writing the identity to " + devices.resolve("2A3B4C5D"),
              "Trying the new certificate: a login with it and nothing else");
      assertThat(logs.messages(LogLevel.WARN)).isEmpty();
      assertThat(logs.entries())
          .extracting(Logs.Entry::message)
          .as("the log says what is done and never shows the request")
          .noneMatch(message -> message.contains("CERTIFICATE REQUEST"));
    }
  }

  @Nested
  @DisplayName("the request that is sent")
  class Request {

    @BeforeEach
    void retrieveIt() {
      result = retrieve(false);
    }

    @Test
    @DisplayName(
        "is one POST to login.cgi with request_version=3 and cgiauth=1, in"
            + " application/octet-stream, which is what the device requires")
    void isTheRequestTheDeviceRequires() {
      var requests = fixture.device().authenticationRequests();

      assertThat(requests).hasSize(1);
      assertThat(requests.getFirst().query()).isEqualTo("request_version=3&cgiauth=1");
      assertThat(requests.getFirst().contentType()).isEqualTo("application/octet-stream");
    }

    @Test
    @DisplayName("has the request as PEM for its body, and never the private key")
    void hasTheRequestAsTheBody() {
      var body = fixture.device().authenticationRequests().getFirst().body();

      assertThat(body)
          .startsWith("-----BEGIN CERTIFICATE REQUEST-----\n")
          .endsWith("-----END CERTIFICATE REQUEST-----\n");
      assertThat(body).doesNotContain("PRIVATE");
    }

    @Test
    @DisplayName(
        "has version 0, one entry in its subject which is the common name, and no attributes")
    void hasTheNameOfTheWindowsClient() {
      var information =
          TestDer.read(
                  Pem.first(
                          fixture.device().authenticationRequests().getFirst().body(),
                          "CERTIFICATE REQUEST")
                      .orElseThrow()
                      .content(),
                  0)
              .children()
              .getFirst()
              .children();

      var subject = information.get(1);
      var entry = subject.children().getFirst().children().getFirst().children();
      assertThat(information.get(0).encoded()).isEqualTo(new byte[] {0x02, 0x01, 0x00});
      assertThat(subject.children()).hasSize(1);
      assertThat(new String(entry.get(1).content(), StandardCharsets.UTF_8)).isEqualTo(COMMON_NAME);
      assertThat(information.get(3).encoded()).isEqualTo(new byte[] {(byte) 0xA0, 0x00});
    }

    @Test
    @DisplayName("is signed with SHA-1 and RSA by the key that it holds: the signature verifies")
    void isSignedWithSha1AndRsa() throws Exception {
      var parts =
          TestDer.read(
                  Pem.first(
                          fixture.device().authenticationRequests().getFirst().body(),
                          "CERTIFICATE REQUEST")
                      .orElseThrow()
                      .content(),
                  0)
              .children();
      var publicKey =
          KeyFactory.getInstance("RSA")
              .generatePublic(new X509EncodedKeySpec(parts.get(0).children().get(2).encoded()));

      var verifier = Signature.getInstance("SHA1withRSA");
      verifier.initVerify(publicKey);
      verifier.update(parts.get(0).encoded());
      var signature = parts.get(2).content();

      assertThat(parts.get(1).encoded())
          .isEqualTo(SignatureScheme.SHA1_WITH_RSA.algorithmIdentifier());
      assertThat(verifier.verify(Arrays.copyOfRange(signature, 1, signature.length))).isTrue();
    }
  }

  @Nested
  @DisplayName("the trial of the certificate")
  class Trying {

    @Test
    @DisplayName(
        "is not made when it is switched off, and the verification says that it was skipped")
    void isNotMadeWhenSwitchedOff() {
      result = retrieve(false);

      assertThat(trials).isEmpty();
      assertThat(result.verification()).isEqualTo(Verification.skipped());
      assertThat(logs.messages(LogLevel.INFO))
          .noneMatch(message -> message.startsWith("Trying the new certificate"));
    }

    @Test
    @DisplayName(
        "that fails is a warning, with the device's words, and the certificate is kept all the"
            + " same")
    void aFailedTrialIsAWarning() {
      var refusal = new ServiceError(CoreErrorCode.COMMAND_FAILED, "peer not authenticated");
      trialOutcome = Verification.failed(refusal);

      result = retrieve(true);

      assertThat(result.verification()).isEqualTo(Verification.failed(refusal));
      assertThat(logs.messages(LogLevel.WARN))
          .containsExactly("The device did not take the new certificate: peer not authenticated");
      assertThat(directory.existingFiles()).hasSize(3);
    }
  }
}
