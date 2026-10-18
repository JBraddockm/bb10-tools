/**
 * Pairing a computer with a device, as BlackBerry Link does: the device signs a certificate for the
 * login on port 8443 ({@code login.cgi}), and takes the authority of the computer so that it lists
 * the computer in Device Connections ({@code pairing.cgi}). What is made is kept in a folder for
 * each device.
 *
 * <p>The two sub-packages hold what does not depend on the rest of bb-link, so that they can move
 * to a library of their own for other tools that pair with a device: {@code pki} (DER, certificate
 * requests, certificates and authorities, PEM, and what a certificate says) uses the JDK and
 * nothing else, and {@code store} (the folders of a certificate and of a pairing) uses {@code pki}
 * and the JSON of the core. A check reads their imports and fails if they reach further.
 */
package com.jbraddockm.link.pairing;
