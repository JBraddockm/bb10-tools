/**
 * Public-key plumbing for enrolling a client: DER encoding, certificate requests, PEM and the
 * details of a certificate.
 *
 * <p>This package uses the JDK and nothing else, and knows nothing of devices, CGI programs or
 * sessions, so that it can move to a library of its own without a change. A check keeps it so.
 */
package com.jbraddockm.link.pairing.pki;
