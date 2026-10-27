package com.jbraddockm.link.endpoint.pairing;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.store.IdentityDirectory;
import com.jbraddockm.link.testing.DeviceFixture;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for {@link CertificateRetrieval}: refusals, and what is left behind when retrieval fails.
 */
@DisplayName("CertificateRetrieval: what it refuses")
class CertificateRetrievalRefusalsTest {

  @TempDir private Path devices;
  private DeviceFixture fixture;

  private final CertificateRetrieval retrieval =
      new CertificateRetrieval(
          (_, _, _, _, _) -> {
            throw new AssertionError("a certificate that was not kept must not be tried");
          },
          Clock.systemUTC());

  @BeforeEach
  void openTheDevice() {
    fixture = DeviceFixture.withAuthority();
  }

  @AfterEach
  void closeTheDevice() {
    fixture.close();
  }

  private CertificateRetrievalRequest request(Path folder, String host, boolean replace) {
    return new CertificateRetrievalRequest("2A3B4C5D", folder, host, replace, false, 8443);
  }

  private CertificateRetrievalRequest request() {
    return request(devices, "workstation", false);
  }

  private void assertNothingKept() throws IOException {
    assertThat(IdentityDirectory.of(devices, "2A3B4C5D").existingFiles()).isEmpty();
    var folder = devices.resolve("2A3B4C5D");
    if (Files.exists(folder)) {
      try (var names = Files.list(folder)) {
        assertThat(names.toList()).isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("an answer of the device that is not a certificate for our key")
  class Answers {

    @Test
    @DisplayName(
        "a success with no certificate is not a certificate: it is unexpected, with the status")
    void aSuccessWithoutACertificate() throws Exception {
      fixture.device().authenticationAnswer =
          Resources.text(
              CertificateRetrievalRefusalsTest.class,
              "authentication-answer--success-without-certificate.xml");

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failure.error().message())
          .isEqualTo("The device answered login.cgi without a certificate");
      assertThat(failure.error().details()).containsEntry("status", "Success");
      assertNothingKept();
    }

    @Test
    @DisplayName("a text that is neither XML nor a certificate is unexpected too, and is shown")
    void aTextThatIsNothing() throws Exception {
      fixture.device().authenticationAnswer =
          Resources.text(
              CertificateRetrievalRefusalsTest.class, "authentication-answer--not-xml-not-pem.txt");

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(String.valueOf(failure.error().details().get("answer")))
          .contains("something else entirely");
      assertNothingKept();
    }

    @Test
    @DisplayName(
        "the real error 260 for another content type fails with the device's words and its number")
    void theRealError260() throws Exception {
      fixture.device().authenticationAnswer =
          Resources.text(
              CertificateRetrievalRefusalsTest.class,
              "authentication-answer--content-type-error.xml");

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failure.error().message())
          .isEqualTo("Expected ContentType of application/octet-stream");
      assertThat(failure.error().details()).containsEntry("errorIdentifier", 260);
      assertNothingKept();
    }

    @Test
    @DisplayName("a certificate block that is not a certificate is rejected, and nothing is kept")
    void aBlockThatIsNotACertificate() throws Exception {
      fixture.device().authenticationAnswer =
          Resources.text(
              CertificateRetrievalRefusalsTest.class,
              "authentication-answer--garbage-certificate.pem");

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.CERTIFICATE_REJECTED);
      assertThat(failure.error().message()).contains("not X.509");
      assertNothingKept();
    }

    @Test
    @DisplayName(
        "a certificate for another key is rejected, which is what keeps a key from being kept with"
            + " a certificate that is not its own")
    void aCertificateForAnotherKey() throws Exception {
      fixture.device().authenticationForAnotherKey = true;

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.CERTIFICATE_REJECTED);
      assertThat(failure.error().message()).contains("is not for the key that made the request");
      assertNothingKept();
    }

    @Test
    @DisplayName(
        "a device that has nothing to sign with says error 1, as the real one does, and nothing is"
            + " kept")
    void aDeviceThatCannotSign() throws Exception {
      fixture.close();
      fixture = DeviceFixture.withoutAuthority();

      var failure = failed(retrieval.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failure.error().details()).containsEntry("errorIdentifier", 1);
      assertNothingKept();
    }
  }

  @Nested
  @DisplayName("what is already there, and who asks")
  class Guards {

    private final CertificateRetrieval plain =
        new CertificateRetrieval((_, _, _, _, _) -> Verification.skipped(), Clock.systemUTC());

    @Test
    @DisplayName(
        "an identity that is there is not replaced unless that is asked, and nothing is sent")
    void anIdentityThatIsThere() throws Exception {
      succeeded(plain.run(fixture.session(), request(), new Logs()));
      var before = Files.readString(IdentityDirectory.of(devices, "2A3B4C5D").keyFile());

      var failure = failed(plain.run(fixture.session(), request(), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.IDENTITY_EXISTS);
      assertThat(failure.error().message())
          .contains("an identity for this device")
          .contains("replace it");
      assertThat(failure.error().details())
          .containsEntry("directory", devices.resolve("2A3B4C5D").toString());
      assertThat(fixture.device().authenticationRequests()).hasSize(1);
      assertThat(Files.readString(IdentityDirectory.of(devices, "2A3B4C5D").keyFile()))
          .isEqualTo(before);
    }

    @Test
    @DisplayName(
        "an identity that is there is replaced when that is asked: a new key, one request more, and"
            + " the new identity file")
    void replacingAnIdentity() throws Exception {
      var first = succeeded(plain.run(fixture.session(), request(), new Logs()));
      var keyBefore = Files.readString(first.keyFile());

      var second =
          succeeded(plain.run(fixture.session(), request(devices, "another", true), new Logs()));

      assertThat(Files.readString(second.keyFile())).isNotEqualTo(keyBefore);
      assertThat(fixture.device().authenticationRequests()).hasSize(2);
      assertThat(IdentityDirectory.of(devices, "2A3B4C5D").metadata().orElseThrow().commonName())
          .isEqualTo("BBCGIAUTH [2A3B4C5D] another");
    }

    @Test
    @DisplayName(
        "a PIN that is not the device's is refused before anything is made: nothing is sent, and no"
            + " folder is made")
    void aPinThatIsNotTheDevices() throws Exception {
      var wrong = new CertificateRetrievalRequest("FFFFFFFF", devices, null, false, false, 8443);

      var failure = failed(retrieval.run(fixture.session(), wrong, new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.PIN_MISMATCH);
      assertThat(failure.error().message())
          .as("the error does not reveal the PIN")
          .doesNotContain("2A3B4C5D");
      assertThat(fixture.device().authenticationRequests()).isEmpty();
      assertThat(fixture.device().cgiRequests.get()).as("only the PIN was read").isEqualTo(1);
      try (var names = Files.list(devices)) {
        assertThat(names.toList()).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "a folder that cannot be written fails as an error of writing, naming the folder, and"
            + " nothing is sent")
    void aFolderThatCannotBeWritten() throws Exception {
      var notAFolder = Files.writeString(devices.resolve("a-file"), "not a folder");

      var failure =
          failed(
              retrieval.run(
                  fixture.session(), request(notAFolder, "workstation", false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
      assertThat(failure.error().message())
          .startsWith("Could not write the identity in " + notAFolder);
      assertThat(fixture.device().authenticationRequests()).isEmpty();
    }
  }

  @Nested
  @DisplayName("the name of the computer in the common name")
  class Names {

    private final CertificateRetrieval plain =
        new CertificateRetrieval((_, _, _, _, _) -> Verification.skipped(), Clock.systemUTC());

    @Test
    @DisplayName("is cut to 19 characters, which keeps the request within what the device takes")
    void isCutTo19Characters() {
      var longName =
          Resources.text(CertificateRetrievalRefusalsTest.class, "host-name--30-characters.txt")
              .strip();
      var expected =
          Resources.text(CertificateRetrievalRefusalsTest.class, "common-name--cut-host.txt")
              .strip();

      var result =
          succeeded(plain.run(fixture.session(), request(devices, longName, false), new Logs()));

      assertThat(result.commonName()).isEqualTo(expected);
    }

    @Test
    @DisplayName("has the characters that the device may not take written as dashes")
    void isMadeSafe() {
      var result =
          succeeded(plain.run(fixture.session(), request(devices, "my host", false), new Logs()));

      assertThat(result.commonName()).isEqualTo("BBCGIAUTH [2A3B4C5D] my-host");
    }

    @Test
    @DisplayName("is the name of this computer when none is given, safe to write")
    void isThisComputersWhenNoneIsGiven() {
      var result =
          succeeded(plain.run(fixture.session(), request(devices, null, false), new Logs()));

      assertThat(result.commonName()).matches("BBCGIAUTH \\[2A3B4C5D] [A-Za-z0-9._-]{1,19}");
    }
  }
}
