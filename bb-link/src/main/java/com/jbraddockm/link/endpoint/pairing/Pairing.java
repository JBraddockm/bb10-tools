package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.endpoint.pairing.pki.AuthorityCertificate;
import com.jbraddockm.link.endpoint.pairing.pki.EcKeys;
import com.jbraddockm.link.endpoint.pairing.pki.SignatureScheme;
import com.jbraddockm.link.endpoint.pairing.store.DeviceId;
import com.jbraddockm.link.endpoint.pairing.store.FilesExistException;
import com.jbraddockm.link.endpoint.pairing.store.PairingDirectory;
import com.jbraddockm.link.endpoint.pairing.store.StoredPairing;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;

/**
 * Pairs this computer with a device, as BlackBerry Link does: the computer makes an authority of
 * its own, the device takes it and lists the computer in Device Connections, and the device answers
 * with its own authority, which is kept.
 *
 * <p>The authority is made like Link's **[verified]**: EC P-521, signed with SHA-1, named {@code PC
 * issuer} and 16 random hexadecimal digits, valid ten years and starting seven days earlier. In
 * order: the PIN is confirmed; the key is written under a temporary name; the authority is sent; and
 * only when the device has answered with its own are the files moved into place.
 *
 * <p>A plain pairing is for a device that is not paired yet: it makes a fresh identifier and refuses
 * when a pairing is already on disk. A renewal re-establishes a pairing that is on disk under the
 * same identifier the device knows — so the device updates its one entry rather than adding a
 * duplicate — reaffirming the stored certificate when it is still there (no change on the device) or
 * re-minting it when the certificates were removed. The device recognises a re-enrollment as {@code
 * Device already paired}, which is a success.
 *
 * <p>This changes the device. It is never repeated after a dropped connection, and the device
 * cannot be told to forget a pairing: that is done on the device, in Device Connections.
 */
public final class Pairing {

  private static final int AUTHORITY_VALIDITY_DAYS = 3650;
  private static final int BACKDATED_DAYS = 7;
  private static final int SERIAL_BITS = 127;
  private static final int NAME_BYTES = 8;

  private final Clock clock;
  private final SecureRandom random;

  /** A pairing with the system clock. */
  public Pairing() {
    this(Clock.systemUTC(), new SecureRandom());
  }

  /**
   * A pairing with its collaborators given, which tests use.
   *
   * @param clock where the time comes from
   * @param random where the identifier, the name, the serial number and the key get their
   *     randomness
   */
  public Pairing(Clock clock, SecureRandom random) {
    this.clock = clock;
    this.random = random;
  }

  /**
   * Pairs this computer with a device.
   *
   * @param session a session to the device; a password session over USB is what is known to work
   * @param request what to do
   * @param listener where progress is told
   * @return the pairing that was made, or why it was not
   */
  public CommandOutcome<PairingResult> run(
      DeviceSession session, PairingRequest request, CommandListener listener) {
    var steps = new DeviceSteps(session, listener);
    try {
      var pin = steps.confirmPin(request.confirmPin());
      var directory = PairingDirectory.of(request.devicesDirectory(), pin);
      var existing = directory.metadata();
      if (request.renew()) {
        // A renewal re-establishes a pairing that is already on disk, under the same identifier the
        // device knows, so it updates its one trusted-peer entry (deviceid is UNIQUE there) instead
        // of adding a duplicate. It needs a pairing to renew.
        if (existing.isEmpty()) {
          return failure(
              CoreErrorCode.INVALID_ARGUMENT,
              "There is no pairing to renew in " + directory.directory() + "; pair the device first",
              Map.of());
        }
        if (canReaffirm(directory)) {
          return reaffirm(steps, pin, directory, existing.get(), listener);
        }
        // The certificates were removed (e.g. by `remove`); re-mint them under the kept identifier.
        return enroll(steps, pin, directory, existing.get().deviceId(), true, listener);
      }
      // A plain pairing is for a device that is not paired yet. If a pairing is already on disk, the
      // caller means to refresh it: that is `renew`, which keeps the identifier.
      if (existing.isPresent()) {
        return failure(
            LinkErrorCode.PAIRING_EXISTS,
            "This computer is already paired with the device in "
                + directory.directory()
                + "; use `renew` to refresh it, or `remove` it first",
            Json.object("directory", directory.directory().toString()));
      }
      return enroll(steps, pin, directory, DeviceId.generate(random), false, listener);
    } catch (DeviceSteps.Stopped stopped) {
      return CommandOutcome.failed(stopped.error());
    } catch (FilesExistException exists) {
      return failure(
          LinkErrorCode.PAIRING_EXISTS,
          exists.getMessage(),
          Json.object("directory", exists.directory()));
    } catch (GeneralSecurityException failure) {
      return failure(
          CoreErrorCode.INTERNAL,
          "Could not make the authority: " + failure.getMessage(),
          Map.of());
    } catch (IOException failure) {
      return failure(
          CoreErrorCode.IO_ERROR,
          "Could not write the pairing in "
              + request.devicesDirectory()
              + ": "
              + failure.getMessage(),
          Map.of());
    }
  }

  /**
   * Mints a fresh authority and tunnel identity, enrols them under {@code deviceId}, and keeps them.
   * This is a first pairing ({@code renew} false) or a renewal whose stored certificates are gone
   * ({@code renew} true, which may replace what is on disk and, if the device answers a re-insert
   * without its own authority, keeps the stored one).
   */
  private CommandOutcome<PairingResult> enroll(
      DeviceSteps steps,
      String pin,
      PairingDirectory directory,
      String deviceId,
      boolean renew,
      CommandListener listener)
      throws GeneralSecurityException, IOException {
    var commonName = "PC issuer " + HexFormat.of().withUpperCase().formatHex(randomBytes());
    var keys = EcKeys.generateP521();
    var now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
    var authority =
        AuthorityCertificate.create(
            keys,
            SignatureScheme.SHA1_WITH_ECDSA,
            commonName,
            new BigInteger(SERIAL_BITS, random).add(BigInteger.ONE),
            now.minus(Duration.ofDays(BACKDATED_DAYS)),
            now.plus(Duration.ofDays(AUTHORITY_VALIDITY_DAYS)));
    var stored = new StoredPairing(deviceId, commonName, clock.instant());
    try (var pending = directory.prepare(keys, renew)) {
      listener.onLog(
          LogLevel.INFO,
          (renew ? "Renewing the pairing with the device as " : "Pairing with the device as ")
              + commonName);
      var returned =
          deviceAuthority(
              steps.run(new PairWithDevice(deviceId, authority.pem())), directory, renew, listener);
      listener.onLog(
          LogLevel.INFO, "The device returned its own authority: " + returned.details().subject());
      pending.commit(authority.pem(), returned.pem(), stored);

      // The tunnel is part of BlackBerry Link — the Wi-Fi endpoints do not work without it. Make a
      // self-signed identity for the tunnel and enrol it through the same pairing the authority
      // used, so the device trusts the certificate the tunnel will present. The device keeps one
      // trusted peer per computer, so this one identity also serves Blend; no separate cert is made.
      var tunnel = serviceIdentity("tunnel", now);
      listener.onLog(LogLevel.INFO, "Enrolling the Wi-Fi tunnel identity with the device");
      // Enrol under the same workstation identifier: the device keeps one peer per computer, so this
      // updates its trusted peer cert to the one the tunnel presents.
      steps.run(new PairWithDevice(deviceId, tunnel.certificate().pem()));
      directory.writeServiceIdentity(
          "tunnel", tunnel.certificate().pem(), tunnel.keys().getPrivate());
      listener.onLog(
          LogLevel.INFO,
          "Paired. The Wi-Fi tunnel identity is enrolled in " + directory.directory());

      return CommandOutcome.succeeded(
          new PairingResult(
              pin,
              deviceId,
              commonName,
              directory.directory(),
              directory.authorityFile(),
              directory.keyFile(),
              directory.deviceAuthorityFile(),
              ReturnedCertificate.parse(authority.pem()).details(),
              returned.details(),
              clock.instant()));
    }
  }

  /**
   * Reaffirms a pairing that is still on disk: re-sends the stored tunnel certificate — the one the
   * device trusts for this computer — under the same identifier, so the device refreshes its entry
   * and the certificate does not change. Nothing on disk is rewritten.
   */
  private CommandOutcome<PairingResult> reaffirm(
      DeviceSteps steps,
      String pin,
      PairingDirectory directory,
      StoredPairing stored,
      CommandListener listener)
      throws GeneralSecurityException, IOException {
    var tunnelPem =
        Files.readString(directory.serviceCertificateFile("tunnel"), StandardCharsets.UTF_8);
    listener.onLog(
        LogLevel.INFO, "Reaffirming the pairing with the device as " + stored.commonName());
    steps.run(new PairWithDevice(stored.deviceId(), tunnelPem));
    listener.onLog(
        LogLevel.INFO,
        "Reaffirmed. The pairing in " + directory.directory() + " is unchanged and still enrolled");
    var authority =
        ReturnedCertificate.parse(
            Files.readString(directory.authorityFile(), StandardCharsets.UTF_8));
    var deviceAuthority =
        ReturnedCertificate.parse(
            Files.readString(directory.deviceAuthorityFile(), StandardCharsets.UTF_8));
    return CommandOutcome.succeeded(
        new PairingResult(
            pin,
            stored.deviceId(),
            stored.commonName(),
            directory.directory(),
            directory.authorityFile(),
            directory.keyFile(),
            directory.deviceAuthorityFile(),
            authority.details(),
            deviceAuthority.details(),
            clock.instant()));
  }

  /** Whether the pairing's files are all on disk, so it can be reaffirmed rather than re-minted. */
  private static boolean canReaffirm(PairingDirectory directory) {
    return Files.exists(directory.authorityFile())
        && Files.exists(directory.keyFile())
        && Files.exists(directory.deviceAuthorityFile())
        && Files.exists(directory.serviceCertificateFile("tunnel"))
        && Files.exists(directory.directory().resolve("tunnel_key.pem"));
  }

  /**
   * The device's authority from its answer; on a renewal, if the device reports the pairing already
   * present and answers without one, the authority kept on disk is used instead.
   */
  private static ReturnedCertificate deviceAuthority(
      PairingReply reply, PairingDirectory directory, boolean renew, CommandListener listener)
      throws IOException {
    if (reply.deviceAuthority().isEmpty()
        && renew
        && Files.exists(directory.deviceAuthorityFile())) {
      listener.onLog(
          LogLevel.INFO,
          "The device reported the pairing already present and returned no authority; keeping the"
              + " stored one");
      try {
        return ReturnedCertificate.parse(
            Files.readString(directory.deviceAuthorityFile(), StandardCharsets.UTF_8));
      } catch (CertificateException _) {
        throw new DeviceSteps.Stopped(
            LinkErrorCode.CERTIFICATE_REJECTED, "The stored device authority is not X.509", Map.of());
      }
    }
    return deviceAuthorityIn(reply);
  }

  /** The device's authority in its answer, which must be there, and X.509. */
  private static ReturnedCertificate deviceAuthorityIn(PairingReply reply) {
    var text =
        reply
            .deviceAuthority()
            .orElseThrow(
                () ->
                    DeviceSteps.unexpected(
                        "The device answered pairing.cgi without its authority",
                        reply.statusText(),
                        String.valueOf(reply.body().value())));
    try {
      return ReturnedCertificate.parse(text);
    } catch (CertificateException _) {
      throw new DeviceSteps.Stopped(
          LinkErrorCode.CERTIFICATE_REJECTED,
          "The device returned an authority that is not X.509",
          Map.of());
    }
  }

  /** A self-signed service identity (the tunnel), the same shape as the pairing authority. */
  private record ServiceIdentity(KeyPair keys, AuthorityCertificate certificate) {}

  /** Makes a self-signed P-521 identity named {@code commonName}, valid like the authority. */
  private ServiceIdentity serviceIdentity(String commonName, java.time.Instant now)
      throws GeneralSecurityException {
    var keys = EcKeys.generateP521();
    var certificate =
        AuthorityCertificate.create(
            keys,
            SignatureScheme.SHA1_WITH_ECDSA,
            commonName,
            new BigInteger(SERIAL_BITS, random).add(BigInteger.ONE),
            now.minus(Duration.ofDays(BACKDATED_DAYS)),
            now.plus(Duration.ofDays(AUTHORITY_VALIDITY_DAYS)));
    return new ServiceIdentity(keys, certificate);
  }

  private byte[] randomBytes() {
    var bytes = new byte[NAME_BYTES];
    random.nextBytes(bytes);
    return bytes;
  }

  private static CommandOutcome<PairingResult> failure(
      ErrorCode code, String message, Map<String, Object> details) {
    return CommandOutcome.failed(new ServiceError(code, message, details));
  }
}
