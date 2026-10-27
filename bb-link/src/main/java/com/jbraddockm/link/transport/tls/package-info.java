/**
 * The client TLS identity and trust for talking to a device: {@link
 * com.jbraddockm.link.transport.tls.ClientCertificate} and the {@link
 * com.jbraddockm.link.transport.tls.PemIdentity} that loads it, the {@link
 * com.jbraddockm.link.transport.tls.FixedClientKeyManager} that presents it, and {@link
 * com.jbraddockm.link.transport.tls.TrustAllTls} for the device's self-signed certificate. JDK-only,
 * so it depends on nothing else in bb-link.
 */
package com.jbraddockm.link.transport.tls;
