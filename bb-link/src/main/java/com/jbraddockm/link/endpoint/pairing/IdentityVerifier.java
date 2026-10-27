package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.NoData;
import com.jbraddockm.link.endpoint.actions.ConfirmedByPin;
import com.jbraddockm.link.transport.session.Authentication;
import com.jbraddockm.link.transport.session.ClientCertificate;
import com.jbraddockm.link.transport.session.DeviceSession;
import com.jbraddockm.link.transport.session.LoginOnly;
import com.jbraddockm.link.transport.session.TransportScheme;

/** Tries a new identity against the device that issued its certificate. */
@FunctionalInterface
interface IdentityVerifier {

  /**
   * Tries the identity.
   *
   * @param session the session that did the enrolment; the trial goes to the same host
   * @param identity the files of the new identity
   * @param port the port that takes certificates
   * @param pin the PIN that the device must give when asked, as a check that it is the right one
   * @param listener where the trial reports
   * @return whether the device took the identity
   */
  Verification verify(
      DeviceSession session,
      ClientCertificate identity,
      int port,
      String pin,
      CommandListener listener);

  /**
   * A trial that logs in with the identity alone, over TLS, and has the device confirm its PIN. The
   * only request after the login is the one that reads the PIN.
   *
   * @return the verifier
   */
  static IdentityVerifier byLoggingIn() {
    return IdentityVerifier::logInAndReadThePin;
  }

  private static Verification logInAndReadThePin(
      DeviceSession session,
      ClientCertificate identity,
      int port,
      String pin,
      CommandListener listener) {
    try (var certificateSession =
        session.sibling(TransportScheme.HTTPS, port, new Authentication.Certificate(identity))) {
      return switch (certificateSession.execute(
          new ConfirmedByPin<>(new LoginOnly(), pin), listener)) {
        case CommandOutcome.Succeeded<NoData> _ -> Verification.verified();
        case CommandOutcome.Failed<NoData>(var error, _, _) -> Verification.failed(error);
      };
    }
  }
}
