/**
 * The client TLS identity and trust for talking to a device: {@link
 * com.jbraddockm.link.transport.tls.ClientCertificate} and the {@link
 * com.jbraddockm.link.transport.tls.PemIdentity} that loads it, the {@link
 * com.jbraddockm.link.transport.tls.FixedClientKeyManager} that presents it, and {@link
 * com.jbraddockm.link.transport.tls.TrustAllTls} for the device's self-signed certificate. Depends
 * only on {@code transport.internal}, {@code core.error}, and the HTTP client, so it could lift to
 * {@code bb-core} alongside {@code transport.internal}.
 */
package com.jbraddockm.link.transport.tls;
