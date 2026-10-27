package com.jbraddockm.link.endpoint.pairing;

import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.endpoint.pairing.pki.Pem;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.testing.DeviceFixture;
import com.jbraddockm.link.testing.FixedRandom;
import com.jbraddockm.link.testing.FormBodies;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.math.BigInteger;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link Pairing}: what is stored and sent when the device accepts the authority. Time
 * and randomness are fixed.
 */
@DisplayName("Pairing: a pairing that the device takes")
class PairingTest {

  private static final Instant NOW = Instant.parse("2026-10-18T10:00:00Z");

  @TempDir private Path devices;
  private DeviceFixture fixture;
  private Logs logs;
  private PairingResult result;
  private PairingDirectory directory;
  private Map<?, ?> expected;

  @BeforeEach
  void pair() {
    fixture = DeviceFixture.withAuthority();
    logs = new Logs();
    expected = (Map<?, ?>) Json.parse(Resources.text(PairingTest.class, "pairing--expected.json"));
    var pairing = new Pairing(Clock.fixed(NOW, ZoneOffset.UTC), new FixedRandom(0xAB));
    result =
        succeeded(
            pairing.run(fixture.session(), new PairingRequest("2A3B4C5D", devices, false), logs));
    directory = PairingDirectory.of(devices, "2A3B4C5D");
  }

  @AfterEach
  void closeTheDevice() {
    fixture.close();
  }

  private CertificateDetails authorityOnDisk() throws Exception {
    return CertificateDetails.of(
        CertificateDetails.firstCertificate(Files.readString(directory.authorityFile())));
  }

  @Nested
  @DisplayName("what it says")
  class Saying {

    @Test
    @DisplayName(
        "gives the PIN, and the identifier and the name that it made, and where they are kept")
    void givesTheIdentity() {
      assertThat(result.pin()).isEqualTo("2A3B4C5D");
      assertThat(result.deviceId()).isEqualTo(expected.get("deviceId"));
      assertThat(result.commonName()).isEqualTo(expected.get("commonName"));
      assertThat(result.directory()).isEqualTo(devices.resolve("2A3B4C5D"));
      assertThat(result.authorityFile()).isEqualTo(directory.authorityFile());
      assertThat(result.keyFile()).isEqualTo(directory.keyFile());
      assertThat(result.deviceAuthorityFile()).isEqualTo(directory.deviceAuthorityFile());
    }

    @Test
    @DisplayName("describes the two authorities as the files hold them")
    void describesTheAuthorities() throws Exception {
      assertThat(result.authority()).isEqualTo(authorityOnDisk());
      assertThat(result.deviceAuthority())
          .isEqualTo(
              CertificateDetails.of(
                  CertificateDetails.firstCertificate(
                      Files.readString(directory.deviceAuthorityFile()))));
    }

    @Test
    @DisplayName("counts the days from the time of the clock")
    void usesTheClock() {
      assertThat(result.pairedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName(
        "logs what it does at the level of information, in order, and has nothing to warn of")
    void logsWhatItDoes() {
      assertThat(logs.messages(LogLevel.INFO))
          .containsSubsequence(
              "Pairing with the device as " + expected.get("commonName"),
              "The device returned its own authority: " + result.deviceAuthority().subject());
      assertThat(logs.messages(LogLevel.WARN)).isEmpty();
    }
  }

  @Nested
  @DisplayName("the authority that it makes")
  class Authority {

    @Test
    @DisplayName("is a certificate authority that signed itself, with ECDSA and SHA-1, on P-521")
    void isAnEllipticCurveAuthority() throws Exception {
      var details = authorityOnDisk();

      assertThat(details.certificateAuthority()).isTrue();
      assertThat(details.subject())
          .isEqualTo("CN=" + expected.get("commonName"))
          .isEqualTo(details.issuer());
      assertThat(details.signatureAlgorithm()).isEqualTo("SHA1withECDSA");
      assertThat(details.publicKeyAlgorithm()).isEqualTo("EC");
      assertThat(details.publicKeyBits()).isEqualTo(521);
    }

    @Test
    @DisplayName(
        "has the serial number of the random source, and is valid from seven days before the clock"
            + " for 3650 days")
    void hasTheSerialAndTheValidity() throws Exception {
      var details = authorityOnDisk();

      assertThat(details.serialNumber())
          .isEqualTo(new BigInteger((String) expected.get("serialNumber")));
      assertThat(details.notBefore()).isEqualTo(Instant.parse((String) expected.get("notBefore")));
      assertThat(details.notAfter()).isEqualTo(Instant.parse((String) expected.get("notAfter")));
    }

    @Test
    @DisplayName(
        "has a private key in the folder that is the key of the authority: what it signs, the"
            + " authority verifies")
    void theKeyIsTheKeyOfTheAuthority() throws Exception {
      var pkcs8 =
          Pem.first(Files.readString(directory.keyFile()), "PRIVATE KEY").orElseThrow().content();
      var privateKey = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(pkcs8));
      var certificate =
          CertificateDetails.firstCertificate(Files.readString(directory.authorityFile()));
      var data = new byte[] {1, 2, 3};

      var signer = Signature.getInstance("SHA256withECDSA");
      signer.initSign(privateKey);
      signer.update(data);
      var signature = signer.sign();
      var verifier = Signature.getInstance("SHA256withECDSA");
      verifier.initVerify(certificate.getPublicKey());
      verifier.update(data);

      assertThat(verifier.verify(signature)).isTrue();
    }

    @Test
    @DisplayName(
        "keeps the key where only its owner can read it, in a folder that only its owner can enter")
    void keepsTheKeyPrivate() throws Exception {
      assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));

      assertThat(Files.getPosixFilePermissions(directory.keyFile()))
          .isEqualTo(PosixFilePermissions.fromString("rw-------"));
      assertThat(Files.getPosixFilePermissions(directory.directory()))
          .isEqualTo(PosixFilePermissions.fromString("rwx------"));
    }
  }

  @Nested
  @DisplayName("what it sends and keeps")
  class Sending {

    @Test
    @DisplayName(
        "sends the authority first — the identifier and the authority — then enrols the tunnel"
            + " identity: two pairing requests")
    void sendsTheTwoFields() throws Exception {
      var bodies = fixture.device().pairingBodies();

      assertThat(bodies).hasSize(2);
      var authority = FormBodies.parse(bodies.getFirst());
      assertThat(List.copyOf(authority.keySet())).containsExactly("deviceid", "certdata");
      assertThat(authority.get("deviceid")).isEqualTo(expected.get("deviceId"));
      assertThat(authority.get("certdata")).isEqualTo(Files.readString(directory.authorityFile()));

      var tunnel = FormBodies.parse(bodies.get(1));
      assertThat(tunnel.get("certdata"))
          .isEqualTo(Files.readString(directory.serviceCertificateFile("tunnel")));
    }

    @Test
    @DisplayName("leaves the device holding the tunnel identity under the computer's identifier")
    void theDeviceKeepsTheTunnelIdentity() throws Exception {
      // The authority is enrolled first, then the tunnel identity under the same identifier, so the
      // device's trusted peer for this computer ends as the cert the tunnel presents.
      assertThat(fixture.device().pairings().get(result.deviceId()))
          .isEqualTo(Files.readString(directory.serviceCertificateFile("tunnel")));
    }

    @Test
    @DisplayName("never sends the private key")
    void neverSendsThePrivateKey() {
      assertThat(fixture.device().pairingBodies()).noneMatch(body -> body.contains("PRIVATE"));
    }

    @Test
    @DisplayName("keeps the authority that the device answered with, as it gave it")
    void keepsTheAuthorityOfTheDevice() throws Exception {
      assertThat(Files.readString(directory.deviceAuthorityFile()))
          .isEqualTo(fixture.device().authority.rootPem());
    }

    @Test
    @DisplayName(
        "keeps four files, the pairing file being what the identifier, the name and the time say,"
            + " and no temporary file")
    void keepsFourFiles() throws Exception {
      assertThat(directory.existingFiles()).hasSize(4);
      assertThat(directory.metadataFile())
          .hasContent(Resources.text(PairingTest.class, "pairing--stored.json"));
      try (var names = Files.list(directory.directory())) {
        assertThat(names.map(path -> path.getFileName().toString()))
            .noneMatch(name -> name.endsWith(".new"));
      }
    }
  }
}
