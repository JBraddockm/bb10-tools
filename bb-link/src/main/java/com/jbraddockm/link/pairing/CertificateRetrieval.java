package com.jbraddockm.link.pairing;

import com.jbraddockm.core.command.CommandListener;
import com.jbraddockm.core.command.CommandOutcome;
import com.jbraddockm.core.command.LogLevel;
import com.jbraddockm.core.error.CoreErrorCode;
import com.jbraddockm.core.error.ErrorCode;
import com.jbraddockm.core.error.ServiceError;
import com.jbraddockm.core.json.Json;
import com.jbraddockm.link.LinkErrorCode;
import com.jbraddockm.link.pairing.pki.CertificateRequest;
import com.jbraddockm.link.pairing.pki.CertificateRequest.Rdn;
import com.jbraddockm.link.pairing.pki.RsaKeys;
import com.jbraddockm.link.pairing.pki.SignatureScheme;
import com.jbraddockm.link.pairing.store.FilesExistException;
import com.jbraddockm.link.pairing.store.IdentityDirectory;
import com.jbraddockm.link.pairing.store.StoredIdentity;
import com.jbraddockm.link.session.ClientCertificate;
import com.jbraddockm.link.session.DeviceSession;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.cert.CertificateException;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Has the device sign a certificate for this client: the key is made here, a certificate request
 * goes to {@code login.cgi}, and what comes back is checked and kept with the key, for the
 * certificate login on port 8443.
 *
 * <p>The request is made like the one that BlackBerry Link for Windows sent for the certificate of
 * a laptop **[verified]**: an RSA 2048 key; a subject that is the common name {@code BBCGIAUTH
 * [PIN] <host>} and nothing else (the PIN as hexadecimal digits, and the name of the computer);
 * signed with SHA-1. The device keeps the subject as it is sent and adds a serial number to it.
 *
 * <p>In order: the PIN is confirmed; the key is made and written under a temporary name, and
 * nothing is kept if an identity is there and may not be replaced; the request is sent; the
 * certificate in the answer must be X.509 and for the key; only then are the files moved into
 * place; after that the identity is tried, unless that is switched off.
 */
public final class CertificateRetrieval {

  /** The longest name of a computer that is written into the common name. */
  private static final int NAME_LENGTH = 19;

  private final IdentityVerifier verifier;
  private final Clock clock;

  /** A retrieval that tries the identity by logging in with it, with the system clock. */
  public CertificateRetrieval() {
    this(IdentityVerifier.byLoggingIn(), Clock.systemUTC());
  }

  /**
   * A retrieval with its collaborators given, which tests use.
   *
   * @param verifier how the identity is tried
   * @param clock where the time comes from
   */
  public CertificateRetrieval(IdentityVerifier verifier, Clock clock) {
    this.verifier = verifier;
    this.clock = clock;
  }

  /**
   * Has the device sign a certificate for this client.
   *
   * @param session a password session to the device
   * @param request what to do
   * @param listener where progress is told
   * @return the identity that was made, or why it was not
   */
  public CommandOutcome<CertificateRetrievalResult> run(
      DeviceSession session, CertificateRetrievalRequest request, CommandListener listener) {
    var steps = new DeviceSteps(session, listener);
    try {
      var pin = steps.confirmPin(request.confirmPin());
      var identity = IdentityDirectory.of(request.devicesDirectory(), pin);
      var commonName =
          "BBCGIAUTH ["
              + ClientNames.pinHex(pin)
              + "] "
              + cut(
                  request
                      .hostName()
                      .map(ClientNames::sanitise)
                      .orElseGet(ClientNames::localWorkstation));
      var keys = RsaKeys.generate(RsaKeys.DEVICE_KEY_BITS);
      var certificateRequest =
          CertificateRequest.withSubject(
              keys, List.of(Rdn.commonName(commonName)), SignatureScheme.SHA1_WITH_RSA);
      var stored = new StoredIdentity(commonName, clock.instant());
      try (var pending = identity.prepare(keys, stored, request.replace())) {
        listener.onLog(LogLevel.INFO, "Asking the device to sign a certificate for " + commonName);
        var issued =
            certificateFor(
                steps.run(new GetAuthenticationCertificate(certificateRequest.pem())), keys);
        listener.onLog(LogLevel.INFO, "Writing the identity to " + identity.directory());
        pending.commit(issued.pem(), stored);
        return CommandOutcome.succeeded(
            new CertificateRetrievalResult(
                pin,
                commonName,
                identity.directory(),
                identity.certificateFile(),
                identity.keyFile(),
                issued.details(),
                verify(session, request, identity, pin, listener),
                clock.instant()));
      }
    } catch (DeviceSteps.Stopped stopped) {
      return CommandOutcome.failed(stopped.error());
    } catch (FilesExistException exists) {
      return failure(
          LinkErrorCode.IDENTITY_EXISTS,
          exists.getMessage(),
          Json.object("directory", exists.directory()));
    } catch (GeneralSecurityException | IllegalArgumentException failure) {
      return failure(
          CoreErrorCode.INTERNAL, "Could not make the request: " + failure.getMessage(), Map.of());
    } catch (IOException failure) {
      return failure(
          CoreErrorCode.IO_ERROR,
          "Could not write the identity in "
              + request.devicesDirectory()
              + ": "
              + failure.getMessage(),
          Map.of());
    }
  }

  /** The certificate in the answer, which must be there, X.509, and for the key of the request. */
  private static ReturnedCertificate certificateFor(AuthenticationAnswer answer, KeyPair keys) {
    var text =
        answer
            .certificate()
            .orElseThrow(
                () ->
                    DeviceSteps.unexpected(
                        "The device answered login.cgi without a certificate",
                        answer.statusText(),
                        String.valueOf(answer.body().value())));
    try {
      var returned = ReturnedCertificate.parse(text);
      if (Arrays.equals(
          returned.certificate().getPublicKey().getEncoded(), keys.getPublic().getEncoded())) {
        return returned;
      }
    } catch (CertificateException _) {
      // The device returned something that is not a certificate: the same refusal as below.
    }
    throw new DeviceSteps.Stopped(
        LinkErrorCode.CERTIFICATE_REJECTED,
        "The device returned a certificate that is not X.509, or is not for the key that made the"
            + " request",
        Map.of());
  }

  private Verification verify(
      DeviceSession session,
      CertificateRetrievalRequest request,
      IdentityDirectory identity,
      String pin,
      CommandListener listener) {
    if (!request.verify()) {
      return Verification.skipped();
    }
    listener.onLog(LogLevel.INFO, "Trying the new certificate: a login with it and nothing else");
    var verification =
        verifier.verify(
            session,
            new ClientCertificate(identity.certificateFile(), identity.keyFile()),
            request.verifyPort(),
            pin,
            listener);
    if (verification.state() == Verification.State.FAILED) {
      listener.onLog(
          LogLevel.WARN,
          "The device did not take the new certificate: "
              + verification.error().map(ServiceError::message).orElse("no reason given"));
    }
    return verification;
  }

  private static String cut(String name) {
    return name.length() > NAME_LENGTH ? name.substring(0, NAME_LENGTH) : name;
  }

  private static CommandOutcome<CertificateRetrievalResult> failure(
      ErrorCode code, String message, Map<String, Object> details) {
    return CommandOutcome.failed(new ServiceError(code, message, details));
  }
}
