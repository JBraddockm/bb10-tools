package com.jbraddockm.tunnel;

import java.io.IOException;
import java.security.SecureRandom;
import org.bouncycastle.asn1.*;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;

/**
 * A {@link BcTlsCrypto} that tolerates the BlackBerry device's malformed certificate.
 *
 * <p><b>Why this class exists.</b> The device's TLS/DTLS leaf certificate is an X.509 <b>version 1</b>
 * certificate (its TBSCertificate has <i>no</i> {@code [0] version} field, which implicitly means v1)
 * yet it still carries a {@code [3] extensions} block, which is only legal in v3. Every strict ASN.1
 * X.509 parser — Java's SUN provider, BouncyCastle's JCA provider, and BouncyCastle's own TLS cert
 * parser — rejects this with <i>"version 1 certificate contains extra data"</i>. There is no lenient
 * flag anywhere to turn this off, so we cannot complete the handshake with a stock parser.
 *
 * <p><b>The fix.</b> We subclass {@code BcTlsCrypto} and intercept certificate creation. When the normal
 * parse throws, we re-encode the DER with an explicit {@code [0]{INTEGER 2}} version tag inserted at the
 * front of the TBSCertificate (turning it into a well-formed v3 cert) and parse that instead. The
 * re-encode changes the signed bytes, so the certificate signature no longer verifies — but that is fine:
 * the device cert is accepted in {@code notifyServerCertificate} <i>without</i> chain validation
 * (trust comes from the pairing relationship, not from the certificate chain), so the signature is never
 * checked.
 *
 * <p><b>Why both overrides.</b> BouncyCastle calls {@link #createCertificate(short, byte[])} (the typed
 * form) when parsing <i>server</i> certificates during the handshake, and {@link #createCertificate(byte[])}
 * when we build our own client credential chain. Both must apply the patch, so both are overridden.
 */
public class LenientCrypto extends BcTlsCrypto {

  public LenientCrypto(SecureRandom r) {
    super(r);
  }

  /**
   * Rewrites a DER-encoded certificate so a strict parser accepts it: if the TBSCertificate is missing
   * the explicit {@code [0] version} field, insert {@code [0]{INTEGER 2}} (= version 3) at the front.
   *
   * <p>A Certificate is {@code SEQUENCE { tbsCertificate, signatureAlgorithm, signatureValue }}. We take
   * the tbsCertificate (index 0), check whether its first element is already a {@code [0]}-tagged version,
   * and if not, prepend one, then reassemble the outer Certificate SEQUENCE with the original
   * signatureAlgorithm (index 1) and signatureValue (index 2) untouched.
   *
   * @return the original bytes if already well-formed, otherwise the re-encoded (v3) bytes
   */
  static byte[] forceV3(byte[] der) throws IOException {
    ASN1Sequence cert = ASN1Sequence.getInstance(der);
    ASN1Sequence tbs = ASN1Sequence.getInstance(cert.getObjectAt(0));

    // Already has an explicit [0] version tag → nothing to do.
    if (tbs.getObjectAt(0) instanceof ASN1TaggedObject
        && ((ASN1TaggedObject) tbs.getObjectAt(0)).getTagNo() == 0) return der;

    // Rebuild the TBSCertificate with [0]{INTEGER 2} (version v3) prepended.
    ASN1EncodableVector newTbs = new ASN1EncodableVector();
    newTbs.add(new DERTaggedObject(true, 0, new ASN1Integer(2))); // [0] EXPLICIT version = 2 (v3)
    for (int i = 0; i < tbs.size(); i++) newTbs.add(tbs.getObjectAt(i));

    // Reassemble the outer Certificate SEQUENCE: new TBS + original sigAlg + original sigValue.
    ASN1EncodableVector newCert = new ASN1EncodableVector();
    newCert.add(new DERSequence(newTbs));
    newCert.add(cert.getObjectAt(1)); // signatureAlgorithm (unchanged)
    newCert.add(cert.getObjectAt(2)); // signatureValue (unchanged; now "wrong" but never verified)
    return new DERSequence(newCert).getEncoded("DER");
  }

  /** Used when we build our own client certificate chain. Patches on parse failure. */
  @Override
  public TlsCertificate createCertificate(byte[] encoding) throws IOException {
    return make(encoding);
  }

  /** Called by BouncyCastle to parse the device's server certificate during the handshake. */
  @Override
  public TlsCertificate createCertificate(short type, byte[] encoding) throws IOException {
    try {
      return super.createCertificate(type, encoding);
    } catch (RuntimeException | IOException ex) {
      return super.createCertificate(type, forceV3(encoding));
    }
  }

  private TlsCertificate make(byte[] encoding) throws IOException {
    try {
      return super.createCertificate(encoding);
    } catch (RuntimeException | IOException ex) {
      return super.createCertificate(forceV3(encoding));
    }
  }
}
