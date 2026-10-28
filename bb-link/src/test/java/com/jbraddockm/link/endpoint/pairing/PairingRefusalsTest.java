package com.jbraddockm.link.endpoint.pairing;

import static com.jbraddockm.link.testing.Outcomes.failed;
import static com.jbraddockm.link.testing.Outcomes.succeeded;
import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.testing.DeviceFixture;
import com.jbraddockm.link.testing.Logs;
import com.jbraddockm.link.testing.Resources;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link Pairing}: refusals, and what is left behind when pairing fails. */
@DisplayName("Pairing: what it refuses")
class PairingRefusalsTest {

  @TempDir private Path devices;
  private DeviceFixture fixture;

  @AfterEach
  void closeTheDevice() {
    if (fixture != null) {
      fixture.close();
    }
  }

  private Pairing pairing() {
    return new Pairing();
  }

  private PairingRequest request(Path folder, boolean renew) {
    return new PairingRequest("2A3B4C5D", folder, renew);
  }

  private void assertNothingKept() throws IOException {
    assertThat(PairingDirectory.of(devices, "2A3B4C5D").existingFiles()).isEmpty();
    var folder = devices.resolve("2A3B4C5D");
    if (Files.exists(folder)) {
      try (var names = Files.list(folder)) {
        assertThat(names).isEmpty();
      }
    }
  }

  @Nested
  @DisplayName("an answer of the device that is not its authority")
  class Answers {

    @Test
    @DisplayName(
        "a success with no authority is not a pairing: it is unexpected, with the status and the"
            + " answer")
    void aSuccessWithoutAnAuthority() throws Exception {
      fixture = DeviceFixture.withAuthority();
      fixture.device().pairingAnswer =
          Resources.text(PairingRefusalsTest.class, "pairing-reply--success-without-authority.xml");

      var failure = failed(pairing().run(fixture.session(), request(devices, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.UNEXPECTED_RESPONSE);
      assertThat(failure.error().message())
          .isEqualTo("The device answered pairing.cgi without its authority");
      assertThat(failure.error().details()).containsEntry("status", "Success");
      assertThat(String.valueOf(failure.error().details().get("answer"))).contains("Status");
      assertNothingKept();
    }

    @Test
    @DisplayName("an error that the device reports fails with its own words and its number")
    void anErrorTheDeviceReports() throws Exception {
      fixture = DeviceFixture.withAuthority();
      fixture.device().pairingAnswer =
          Resources.text(PairingRefusalsTest.class, "pairing-reply--error.xml");

      var failure = failed(pairing().run(fixture.session(), request(devices, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failure.error().message()).isEqualTo("Error - insert certificate failed.");
      assertThat(failure.error().details()).containsEntry("errorIdentifier", 7);
      assertNothingKept();
    }

    @Test
    @DisplayName("an authority that is not X.509 is rejected")
    void anAuthorityThatIsNotX509() throws Exception {
      fixture = DeviceFixture.withAuthority();
      fixture.device().pairingAnswer =
          Resources.text(PairingRefusalsTest.class, "pairing-reply--authority-not-x509.xml");

      var failure = failed(pairing().run(fixture.session(), request(devices, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.CERTIFICATE_REJECTED);
      assertThat(failure.error().message())
          .isEqualTo("The device returned an authority that is not X.509");
      assertNothingKept();
    }

    @Test
    @DisplayName(
        "a device that has nothing to pair with answers as the real one does, with error 7, and"
            + " nothing is kept")
    void aDeviceThatCannotPair() throws Exception {
      fixture = DeviceFixture.withoutAuthority();

      var failure = failed(pairing().run(fixture.session(), request(devices, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.COMMAND_FAILED);
      assertThat(failure.error().details()).containsEntry("errorIdentifier", 7);
      assertNothingKept();
    }
  }

  @Nested
  @DisplayName("what is already there, and who asks")
  class Guards {

    @Test
    @DisplayName(
        "a pairing that is there is not paired over: a plain pairing is refused and points to renew,"
            + " and nothing is sent")
    void aPairingThatIsThere() throws Exception {
      fixture = DeviceFixture.withAuthority();
      succeeded(pairing().run(fixture.session(), request(devices, false), new Logs()));
      var before = Files.readString(PairingDirectory.of(devices, "2A3B4C5D").keyFile());

      var failure = failed(pairing().run(fixture.session(), request(devices, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.PAIRING_EXISTS);
      assertThat(failure.error().message()).contains("already paired").contains("renew");
      assertThat(failure.error().details())
          .containsEntry("directory", devices.resolve("2A3B4C5D").toString());
      // The first pairing sent two (authority + tunnel enrolment); the refused one sent nothing more.
      assertThat(fixture.device().pairingBodies()).hasSize(2);
      assertThat(Files.readString(PairingDirectory.of(devices, "2A3B4C5D").keyFile()))
          .isEqualTo(before);
    }

    @Test
    @DisplayName(
        "renewing a pairing whose certificates are still there reaffirms it: the same identifier and"
            + " the same key are kept, and the device keeps one entry (no duplicate)")
    void renewingReaffirms() throws Exception {
      fixture = DeviceFixture.withAuthority();
      var first = succeeded(pairing().run(fixture.session(), request(devices, false), new Logs()));
      var keyBefore = Files.readString(first.keyFile());

      var second = succeeded(pairing().run(fixture.session(), request(devices, true), new Logs()));

      // Reaffirm re-sends the stored certificate under the kept identifier: nothing on disk changes.
      assertThat(second.deviceId()).isEqualTo(first.deviceId());
      assertThat(Files.readString(second.keyFile())).isEqualTo(keyBefore);
      assertThat(PairingDirectory.of(devices, "2A3B4C5D").metadata().orElseThrow().deviceId())
          .isEqualTo(first.deviceId());
      // One identifier, so the device keeps a single entry, not a duplicate.
      assertThat(fixture.device().pairings().keySet()).containsExactly(first.deviceId());
    }

    @Test
    @DisplayName(
        "renewing a pairing whose certificates were removed re-mints them under the kept identifier,"
            + " with a new key, and keeps one device entry")
    void renewingReMintsWhenCertificatesAreGone() throws Exception {
      fixture = DeviceFixture.withAuthority();
      var first = succeeded(pairing().run(fixture.session(), request(devices, false), new Logs()));
      var keyBefore = Files.readString(first.keyFile());
      // Forget the certificates locally but keep the identifier, as `remove` does.
      RemovePairing.remove(devices, "2A3B4C5D");

      var second = succeeded(pairing().run(fixture.session(), request(devices, true), new Logs()));

      assertThat(second.deviceId()).isEqualTo(first.deviceId());
      assertThat(Files.readString(second.keyFile())).isNotEqualTo(keyBefore);
      assertThat(fixture.device().pairings().keySet()).containsExactly(first.deviceId());
    }

    @Test
    @DisplayName("renewing when nothing is paired is refused, and nothing is sent")
    void renewingWithoutAPairing() throws Exception {
      fixture = DeviceFixture.withAuthority();

      var failure = failed(pairing().run(fixture.session(), request(devices, true), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT);
      assertThat(failure.error().message()).contains("no pairing to renew").contains("pair");
      assertThat(fixture.device().pairingBodies()).isEmpty();
    }

    @Test
    @DisplayName(
        "a PIN that is not the device's is refused before anything is made: nothing is sent, and no"
            + " folder is made")
    void aPinThatIsNotTheDevices() throws Exception {
      fixture = DeviceFixture.withAuthority();

      var failure =
          failed(
              pairing()
                  .run(
                      fixture.session(),
                      new PairingRequest("FFFFFFFF", devices, false),
                      new Logs()));

      assertThat(failure.error().code()).isEqualTo(LinkErrorCode.PIN_MISMATCH);
      assertThat(failure.error().message())
          .as("the error does not reveal the PIN")
          .doesNotContain("2A3B4C5D");
      assertThat(fixture.device().pairingBodies()).isEmpty();
      assertThat(fixture.device().cgiRequests.get()).as("only the PIN was read").isEqualTo(1);
      try (var names = Files.list(devices)) {
        assertThat(names).isEmpty();
      }
    }

    @Test
    @DisplayName(
        "a folder that cannot be written fails as an error of writing, naming the folder, and"
            + " nothing is sent")
    void aFolderThatCannotBeWritten() throws Exception {
      fixture = DeviceFixture.withAuthority();
      var notAFolder = Files.writeString(devices.resolve("a-file"), "not a folder");

      var failure =
          failed(pairing().run(fixture.session(), request(notAFolder, false), new Logs()));

      assertThat(failure.error().code()).isEqualTo(CoreErrorCode.IO_ERROR);
      assertThat(failure.error().message())
          .startsWith("Could not write the pairing in " + notAFolder);
      assertThat(fixture.device().pairingBodies()).isEmpty();
    }
  }
}
