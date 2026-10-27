package com.jbraddockm.link.endpoint.pairing;

import com.jbraddockm.link.endpoint.pairing.pki.CertificateDetails;
import com.jbraddockm.link.endpoint.pairing.pki.Pem;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.stream.Collectors;

/**
 * A certificate that the device returned, read and checked to be X.509: the certificate itself,
 * what it says, and the text to keep, which is its PEM blocks and nothing around them.
 *
 * @param certificate the certificate
 * @param details what it says
 * @param pem the text to keep
 */
record ReturnedCertificate(X509Certificate certificate, CertificateDetails details, String pem) {

  /**
   * Reads the first certificate in a text.
   *
   * @param text what the device wrote: PEM, or base64 of the DER, with anything around it
   * @return the certificate
   * @throws CertificateException if there is none, or it is not X.509
   */
  static ReturnedCertificate parse(String text) throws CertificateException {
    var certificate = CertificateDetails.firstCertificate(text);
    var pem =
        Pem.blocks(text, "CERTIFICATE").stream().map(Pem.Block::text).collect(Collectors.joining());
    return new ReturnedCertificate(certificate, CertificateDetails.of(certificate), pem);
  }
}
