package com.jbraddockm.link.transport.session;

import static org.assertj.core.api.Assertions.assertThat;

import javax.net.ssl.KeyManager;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link TrustAllTls}. */
@DisplayName("TrustAllTls: the scheme that accepts the certificate of any device")
@SuppressWarnings("deprecation")
class TrustAllTlsTest {

  @Test
  @DisplayName("is the https scheme, with 8443 as its usual port")
  void isHttpsOn8443() {
    var scheme = TrustAllTls.scheme(null);

    assertThat(scheme.getName()).isEqualTo("https");
    assertThat(scheme.getDefaultPort()).isEqualTo(8443);
  }

  @Test
  @DisplayName(
      "takes any host name, since a device is reached by an address that its certificate does not"
          + " name")
  void takesAnyHostName() {
    var factory = (SSLSocketFactory) TrustAllTls.scheme(null).getSocketFactory();

    assertThat(factory.getHostnameVerifier())
        .isSameAs(SSLSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);
  }

  @Test
  @DisplayName(
      "can be made with no key manager, which is a session with no certificate, and with an empty"
          + " list")
  void canBeMadeWithoutKeyManagers() {
    assertThat(TrustAllTls.scheme(null)).isNotNull();
    assertThat(TrustAllTls.scheme(new KeyManager[0])).isNotNull();
  }
}
