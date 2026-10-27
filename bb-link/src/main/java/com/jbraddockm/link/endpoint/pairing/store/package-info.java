/**
 * The identity of a client on disk: the key, the certificate that a device signed for it, and the
 * identifier that the device knows the client by, in a folder of their own for each device.
 *
 * <p>This package depends on {@code identity.pki} and on the JSON of the core library, and on
 * nothing of bb-link's devices or sessions, so that it can move together with {@code pki}.
 */
package com.jbraddockm.link.endpoint.pairing.store;
