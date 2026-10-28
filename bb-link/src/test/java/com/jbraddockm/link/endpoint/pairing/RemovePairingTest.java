package com.jbraddockm.link.endpoint.pairing;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.error.CoreErrorCode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Tests for {@link RemovePairing}: forgetting a pairing locally, with no device call. */
@DisplayName("RemovePairing: forgetting a pairing on this computer")
class RemovePairingTest {

  @Test
  @DisplayName("deletes the keys and certificates but keeps pairing.json (the identifier)")
  void deletesCertificatesKeepsIdentity(@TempDir Path devices) throws Exception {
    var folder = devices.resolve("2C1E7583");
    Files.createDirectories(folder.resolve("BACKUP"));
    Files.writeString(folder.resolve("tunnel_cert.pem"), "cert");
    Files.writeString(folder.resolve("authority_key.pem"), "key");
    Files.writeString(folder.resolve("BACKUP").resolve("old.pem"), "old");
    Files.writeString(
        folder.resolve("pairing.json"),
        "{\"deviceId\":\"01ABCDEF\",\"commonName\":\"PC\",\"createdAt\":\"2026-10-28T00:00:00Z\"}");

    var outcome = RemovePairing.remove(devices, "2C1E7583");

    assertThat(outcome)
        .isInstanceOfSatisfying(
            CommandOutcome.Succeeded.class,
            succeeded -> {
              var result = (RemovalResult) succeeded.result();
              assertThat(result.pin()).isEqualTo("2C1E7583");
              assertThat(result.deviceId()).isEqualTo("01ABCDEF");
              assertThat(result.toJson())
                  .containsEntry("identityKept", Boolean.TRUE)
                  .containsEntry("deviceStillPaired", Boolean.TRUE)
                  .containsKey("note");
            });
    // pairing.json (the identifier) stays; the keys, certs and backup are gone.
    assertThat(Files.exists(folder.resolve("pairing.json"))).isTrue();
    assertThat(Files.exists(folder.resolve("tunnel_cert.pem"))).isFalse();
    assertThat(Files.exists(folder.resolve("authority_key.pem"))).isFalse();
    assertThat(Files.exists(folder.resolve("BACKUP"))).isFalse();
  }

  @Test
  @DisplayName("succeeds without metadata, reporting no identifier and removing the certificates")
  void removesWithoutMetadata(@TempDir Path devices) throws Exception {
    var folder = devices.resolve("2BD63247");
    Files.createDirectories(folder);
    Files.writeString(folder.resolve("client_cert.pem"), "cert");

    var outcome = RemovePairing.remove(devices, "2BD63247");

    assertThat(outcome)
        .isInstanceOfSatisfying(
            CommandOutcome.Succeeded.class,
            succeeded -> assertThat(((RemovalResult) succeeded.result()).deviceId()).isNull());
    assertThat(Files.exists(folder.resolve("client_cert.pem"))).isFalse();
  }

  @Test
  @DisplayName("fails when there is no such pairing, deleting nothing")
  void failsWhenMissing(@TempDir Path devices) {
    var outcome = RemovePairing.remove(devices, "NOPE1234");

    assertThat(outcome)
        .isInstanceOfSatisfying(
            CommandOutcome.Failed.class,
            failed -> assertThat(failed.error().code()).isEqualTo(CoreErrorCode.INVALID_ARGUMENT));
  }
}
