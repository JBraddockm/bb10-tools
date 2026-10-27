package com.jbraddockm.link.transport.tls;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.X509KeyManager;

/**
 * Offers one client identity to the device, whatever the device asks for.
 *
 * <p>The standard key manager only offers a certificate whose issuer is on the list of acceptable
 * authorities that the server sends. The device's list may name the root while the certificate was
 * issued by an intermediate that only the device holds, in which case the standard manager would
 * send nothing at all. A real client, such as BlackBerry Link, sends its certificate anyway, and
 * the device decides.
 */
public final class FixedClientKeyManager implements X509KeyManager {

  private static final String ALIAS = "bb-link-client";

  private final X509Certificate[] chain;
  private final PrivateKey key;

  public FixedClientKeyManager(PemIdentity.Loaded identity) {
    this.chain = identity.chain().clone();
    this.key = identity.key();
  }

  @Override
  public String[] getClientAliases(String keyType, Principal[] issuers) {
    return new String[] {ALIAS};
  }

  @Override
  public String chooseClientAlias(String[] keyTypes, Principal[] issuers, Socket socket) {
    return ALIAS;
  }

  @Override
  public String[] getServerAliases(String keyType, Principal[] issuers) {
    return null;
  }

  @Override
  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
    return null;
  }

  @Override
  public X509Certificate[] getCertificateChain(String alias) {
    return ALIAS.equals(alias) ? chain.clone() : null;
  }

  @Override
  public PrivateKey getPrivateKey(String alias) {
    return ALIAS.equals(alias) ? key : null;
  }
}
