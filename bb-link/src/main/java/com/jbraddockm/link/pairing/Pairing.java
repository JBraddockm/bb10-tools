package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.pairing.pki.AuthorityCertificate;
import com.jbraddockm.link.pairing.pki.EcKeys;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import com.jbraddockm.link.pairing.store.DeviceId;
import com.jbraddockm.link.pairing.store.FilesExistException;
import com.jbraddockm.link.pairing.store.PairingDirectory;
import com.jbraddockm.link.pairing.store.StoredPairing;
import com.jbraddockm.link.session.DeviceSession;
import java.io.IOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
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
 * order: the PIN is confirmed; the key is written under a temporary name, and nothing is made if a
 * pairing is there and may not be replaced; the authority is sent; and only when the device has
 * answered with its own are the files moved into place.
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
      var deviceId = DeviceId.generate(random);
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
      try (var pending = directory.prepare(keys, request.replace())) {
        listener.onLog(LogLevel.INFO, "Pairing with the device as " + commonName);
        var returned = deviceAuthorityIn(steps.run(new PairWithDevice(deviceId, authority.pem())));
        listener.onLog(
            LogLevel.INFO,
            "The device returned its own authority: " + returned.details().subject());
        pending.commit(authority.pem(), returned.pem(), stored);
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
