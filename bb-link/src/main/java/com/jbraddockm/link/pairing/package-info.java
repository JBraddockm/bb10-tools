/**
 * Enrolling a client certificate on the device through {@code pairing.cgi}, so that the device
 * recognises the client on later connections.
 *
 * <p>This changes the device: the certificate is stored and decides which clients the device
 * accepts. The command is therefore run behind the PIN guard.
 */
package com.jbraddockm.link.pairing;
