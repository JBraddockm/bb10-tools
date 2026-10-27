package com.jbraddockm.tunnel;

import java.io.IOException;
import java.util.Hashtable;
import java.util.List;
import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.tls.*;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedAgreement;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * The BouncyCastle TLS/DTLS client for talking to the device, used for <b>both</b> the control
 * channel (TLS 1.0 over TCP :1111) and the data channel (DTLS 1.0 over UDP). The same configuration
 * works for both; the {@code dtls} flag only selects the protocol version to offer.
 *
 * <p>Everything here is forced to exactly what the device accepts. Getting any of these wrong makes
 * the handshake fail (see the class comments on each override):
 *
 * <ul>
 *   <li>Protocol: TLS 1.0 (control) or DTLS 1.0 (data). The device speaks nothing newer.
 *   <li>Cipher: only {@code TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA} (0xC005), a <b>static-ECDH</b>
 *       suite.
 *   <li>Curve: {@code secp521r1} (P-521) advertised in Supported Groups — the device cert is P-521
 *       and static-ECDH keys the exchange off that cert, so without this the device replies {@code
 *       handshake_failure(40)}.
 *   <li>Client auth: a <b>key-agreement</b> credential (not a signer) — see {@link
 *       #makeClientCredentials}.
 *   <li>Server cert: accepted without validation; trust comes from pairing, not the chain.
 * </ul>
 */
final class TunnelTlsClient extends DefaultTlsClient {
  private final boolean dtls;
  private final List<byte[]> certChainDer;
  private final byte[] keyDer;

  /**
   * @param crypto a {@link LenientCrypto} (so the device's malformed cert parses)
   * @param dtls false → control channel (TLS 1.0); true → data channel (DTLS 1.0)
   * @param certChainDer our client certificate chain as DER blocks, leaf first (from {@link
   *     TunnelConfig})
   * @param keyDer the PKCS#8 DER private key matching the leaf
   */
  TunnelTlsClient(BcTlsCrypto crypto, boolean dtls, List<byte[]> certChainDer, byte[] keyDer) {
    super(crypto);
    this.dtls = dtls;
    this.certChainDer = certChainDer;
    this.keyDer = keyDer;
  }

  /**
   * Builds our client credential from the certificate chain and key supplied by {@link
   * TunnelConfig} (the bundled {@code CN=blend_client_9665} leaf, or whatever the caller enrolled).
   *
   * <p><b>Why an agreement credential, not a signer.</b> For the static-ECDH suite 0xC005,
   * BouncyCastle's {@code TlsECDHKeyExchange} calls {@code requireAgreementCredentials} and rejects
   * a {@code TlsCredentialedSigner} with {@code internal_error(80)}. The client authenticates by
   * performing ECDH directly with its own EC private key against the device certificate's EC key —
   * there is no CertificateVerify signature. So we return a {@link
   * BcDefaultTlsCredentialedAgreement}.
   *
   * <p>The leaf is the device's trusted bp2p peer: {@code tunmgr} verifies it by issuer+hash
   * against the entries in {@code /var/bp2p/trustedpeers}, and the {@code CONNECT} identity ULA is
   * derived from its SHA-1 (see {@link TunnelIdentity}). The key is PKCS#8 and must correspond to
   * the leaf, because it participates directly in the key agreement.
   */
  TlsCredentials makeClientCredentials(BcTlsCrypto crypto) throws Exception {
    TlsCertificate[] certs = new TlsCertificate[certChainDer.size()];
    for (int i = 0; i < certChainDer.size(); i++)
      certs[i] = crypto.createCertificate(certChainDer.get(i));
    Certificate tlsChain = new Certificate(certs);

    AsymmetricKeyParameter privKey = PrivateKeyFactory.createKey(keyDer);
    return new BcDefaultTlsCredentialedAgreement(crypto, tlsChain, privKey);
  }

  /** Offer exactly one protocol version: TLS 1.0 (control) or DTLS 1.0 (data). */
  @Override
  public ProtocolVersion[] getProtocolVersions() {
    return new ProtocolVersion[] {dtls ? ProtocolVersion.DTLSv10 : ProtocolVersion.TLSv10};
  }

  /** Offer exactly the one static-ECDH suite the device uses. */
  @Override
  public int[] getCipherSuites() {
    return new int[] {CipherSuite.TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA};
  }

  /**
   * Advertise secp521r1 in the Supported Groups extension. Required: static-ECDH derives the shared
   * secret from the device's P-521 certificate key, so the client must declare it supports that
   * curve or the device aborts with handshake_failure(40).
   */
  @Override
  public Hashtable<Integer, byte[]> getClientExtensions() throws IOException {
    // BouncyCastle's TLS API declares these extension maps as a raw Hashtable; the one unchecked
    // assignment is where we adopt that raw value into our typed view.
    @SuppressWarnings("unchecked")
    Hashtable<Integer, byte[]> ext = super.getClientExtensions();
    if (ext == null) ext = new Hashtable<>();
    TlsExtensionsUtils.addSupportedGroupsExtension(ext, new int[] {NamedGroup.secp521r1});
    return ext;
  }

  @Override
  public TlsAuthentication getAuthentication() {
    return new TlsAuthentication() {
      /** Accept the device cert unconditionally — trust is from pairing, not the chain. */
      @Override
      public void notifyServerCertificate(TlsServerCertificate c) {
        IO.println(
            (dtls ? "DTLS" : "TLS")
                + " server sent "
                + c.getCertificate().getCertificateList().length
                + " cert(s), accepted");
      }

      /** Supply our key-agreement credential when the device requests a client cert. */
      @Override
      public TlsCredentials getClientCredentials(CertificateRequest req) {
        try {
          return makeClientCredentials((BcTlsCrypto) getCrypto());
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    };
  }
}
