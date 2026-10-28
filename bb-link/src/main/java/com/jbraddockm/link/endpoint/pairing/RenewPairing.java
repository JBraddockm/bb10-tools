package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.link.transport.session.DeviceSession;
import java.time.Clock;

/**
 * Renews everything that this computer keeps for a paired device, over one session, in order:
 *
 * <ol>
 *   <li>the pairing authority — re-enrolled under the identifier the device already knows, so the
 *       device updates its one trusted-peer entry rather than adding a duplicate (reaffirmed when the
 *       certificate is still on disk, re-minted when it is gone);
 *   <li>the device-signed certificate for the port 8443 login — refreshed, since that is the one with
 *       the short life that actually expires.
 * </ol>
 *
 * <p>It needs a pairing to renew: the pairing step fails if none is on disk. If the pairing step
 * succeeds but the certificate step fails, the pairing change has already landed; a renewal is
 * idempotent, so running it again completes the rest.
 */
public final class RenewPairing {

  private final Pairing pairing;
  private final CertificateRetrieval retrieval;
  private final Clock clock;

  /** A renewal with the system clock. */
  public RenewPairing(Pairing pairing, CertificateRetrieval retrieval) {
    this(pairing, retrieval, Clock.systemUTC());
  }

  /**
   * A renewal with its collaborators given, which tests use.
   *
   * @param pairing the pairing, used in renewal mode
   * @param retrieval the certificate issuance, used to replace the login certificate
   * @param clock where the finishing time comes from
   */
  public RenewPairing(Pairing pairing, CertificateRetrieval retrieval, Clock clock) {
    this.pairing = pairing;
    this.retrieval = retrieval;
    this.clock = clock;
  }

  /**
   * Renews a device's pairing and login certificate.
   *
   * @param session a session to the device; a password session over USB is what is known to work
   * @param request what to do
   * @param listener where progress is told
   * @return the renewal that was done, or why it was not
   */
  public CommandOutcome<RenewResult> run(
      DeviceSession session, RenewRequest request, CommandListener listener) {
    var pairingOutcome =
        pairing.run(
            session,
            new PairingRequest(request.confirmPin(), request.devicesDirectory(), true),
            listener);
    return switch (pairingOutcome) {
      case CommandOutcome.Failed<PairingResult> failed -> CommandOutcome.failed(failed.error());
      case CommandOutcome.Succeeded<PairingResult> paired -> refreshCertificate(session, request, paired.result(), listener);
    };
  }

  private CommandOutcome<RenewResult> refreshCertificate(
      DeviceSession session,
      RenewRequest request,
      PairingResult pairing,
      CommandListener listener) {
    var certificateOutcome =
        retrieval.run(
            session,
            new CertificateRetrievalRequest(
                request.confirmPin(),
                request.devicesDirectory(),
                request.hostName(),
                true,
                request.verify(),
                request.verifyPort()),
            listener);
    return switch (certificateOutcome) {
      case CommandOutcome.Failed<CertificateRetrievalResult> failed ->
          CommandOutcome.failed(failed.error());
      case CommandOutcome.Succeeded<CertificateRetrievalResult> issued ->
          CommandOutcome.succeeded(new RenewResult(pairing, issued.result(), clock.instant()));
    };
  }
}
