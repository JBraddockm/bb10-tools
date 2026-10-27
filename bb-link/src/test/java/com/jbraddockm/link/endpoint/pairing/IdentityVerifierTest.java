package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.transport.session.Authentication;
import com.jbraddockm.link.transport.tls.ClientCertificate;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.session.DeviceTarget;
import com.jbraddockm.link.transport.session.TransportScheme;
import com.jbraddockm.link.testing.Resources;
import com.jbraddockm.link.testing.TestPki;
import com.jbraddockm.link.testing.TlsTestDevice;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link IdentityVerifier}: verifying a new certificate by logging in with it. */
@DisplayName("IdentityVerifier: whether the device takes a new certificate")
class IdentityVerifierTest {

  @TempDir private Path folder;
  private TlsTestDevice device;
  private DeviceSession original;

  @BeforeEach
  void startTheDevice() throws Exception {
    device = new TlsTestDevice();
    original =
        DeviceSession.forTarget(
            new DeviceTarget(
                "127.0.0.1", 443, TransportScheme.HTTPS, new Authentication.NoPassword()));
  }

  @AfterEach
  void stopTheDevice() throws Exception {
    original.close();
    device.close();
  }

  private ClientCertificate identity(String key) {
    return new ClientCertificate(
        Resources.copyTo(TestPki.class, "test-pki--leaf-certificate.pem", folder),
        Resources.copyTo(TestPki.class, key, folder));
  }

  private Verification verify(ClientCertificate identity, String pin) {
    return IdentityVerifier.byLoggingIn()
        .verify(original, identity, device.port(), pin, CommandListener.SILENT);
  }

  @Test
  @DisplayName(
      "says verified when the device takes the certificate, which it was shown on every request:"
          + " the login and the confirmation of the PIN")
  void aCertificateThatIsTaken() throws Exception {
    var subject =
        CertificateDetails.firstCertificate(
                Resources.text(TestPki.class, "test-pki--leaf-certificate.pem"))
            .getSubjectX500Principal()
            .getName();

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"), "2A3B4C5D");

    assertThat(verification).isEqualTo(Verification.verified());
    assertThat(device.peers()).containsExactly(subject, subject);
  }

  @Test
  @DisplayName(
      "says failed with PIN_MISMATCH when the device has another PIN, though it took the"
          + " certificate")
  void aPinThatIsNotTheDevices() {
    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"), "FFFFFFFF");

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code()).isEqualTo(LinkErrorCode.PIN_MISMATCH);
  }

  @Test
  @DisplayName("says failed with the words of the certificate when the device refuses it")
  void aCertificateThatIsRefused() {
    device.behaviour = TlsTestDevice.Behaviour.REFUSES_EVERYONE;

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"), "2A3B4C5D");

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code()).isEqualTo(CoreErrorCode.AUTH_FAILED);
    assertThat(verification.error().message()).contains("did not accept the client certificate");
  }

  @Test
  @DisplayName(
      "says failed when the device ends the handshake for a certificate that it did not issue")
  void aHandshakeThatEnds() {
    device.behaviour = TlsTestDevice.Behaviour.REJECTS_PRESENTED_CERTIFICATES;

    var verification = verify(identity("test-pki--leaf-key-pkcs8.pem"), "2A3B4C5D");

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code())
        .isIn(CoreErrorCode.CONNECTION_FAILED, CoreErrorCode.CONNECTION_LOST);
  }

  @Test
  @DisplayName("says failed when the key cannot be used, and has sent nothing")
  void aKeyThatCannotBeUsed() {
    var verification = verify(identity("test-pki--leaf-key-encrypted.pem"), "2A3B4C5D");

    assertThat(verification.state()).isEqualTo(Verification.State.FAILED);
    assertThat(verification.error()).isNotNull();
    assertThat(verification.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
    assertThat(device.paths()).isEmpty();
  }

  @Test
  @DisplayName(
      "does not use the session that it was given: it opens a sibling, on the other port, and"
          + " closes it; the device is asked for its PIN once")
  void theOriginalSessionIsNotUsed() {
    verify(identity("test-pki--leaf-key-pkcs8.pem"), "2A3B4C5D");

    assertThat(original.target().port()).isEqualTo(443);
    assertThat(device.paths())
        .containsExactly("/cgi-bin/login.cgi?request_version=1", "/cgi-bin/wipe.cgi");
  }
}
