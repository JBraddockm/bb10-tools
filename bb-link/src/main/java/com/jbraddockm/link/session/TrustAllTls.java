package com.jbraddockm.link.session;

import com.jbraddockm.link.internal.Impossible;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.SSLSocketFactory;

/**
 * TLS that accepts any certificate and any host name.
 *
 * <p>This is deliberate. Devices present self-signed certificates for addresses such as {@code
 * 169.254.0.1}, so there is nothing to validate against; the original tool does the same. The
 * password is never sent over the connection, only a hash that depends on a challenge from the
 * device.
 */
@SuppressWarnings(
    "deprecation") // deprecated only in newer HttpClient versions; the build pins 4.0.3
final class TrustAllTls {

  private static final int HTTPS_PORT = 8443;

  private TrustAllTls() {}

  /**
   * Creates the scheme.
   *
   * @param keyManagers what to offer if the device asks for a client certificate, or {@code null}
   *     to offer none
   */
  static Scheme scheme(KeyManager[] keyManagers) {
    return Impossible.call(
        () -> {
          var context = SSLContext.getInstance("SSL");
          context.init(
              keyManagers, new TrustManager[] {new AcceptAnyCertificate()}, new SecureRandom());
          var factory = new SSLSocketFactory(context);
          factory.setHostnameVerifier(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
          return new Scheme("https", factory, HTTPS_PORT);
        },
        "the TLS context could not be created");
  }

  /** Trusts everything; see the class description for why. */
  private static final class AcceptAnyCertificate implements X509TrustManager {

    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[0];
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) {
      // devices are never asked for client certificates
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {
      // deliberately accepts every device certificate
    }
  }
}
