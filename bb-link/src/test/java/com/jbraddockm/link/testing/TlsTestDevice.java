package com.jbraddockm.link.testing;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * A device's mutual-TLS listener: it asks for a client certificate, lists as acceptable only a CA
 * that is not the one that issued the test certificate (like a device whose list names the root
 * while the certificate comes from an intermediate), does not insist on one, and answers with what
 * it saw. It speaks just enough of the device's interface for a session: the login, the properties
 * and the PIN.
 */
public final class TlsTestDevice implements AutoCloseable {

  /** What the device does with a request. */
  public enum Behaviour {
    /** Answers normally, whether or not a certificate was presented. */
    ANSWERS_EVERYONE,
    /** Answers with status 200 and nothing at all to a request without a certificate. */
    REFUSES_WITHOUT_CERTIFICATE,
    /** Answers with status 200 and nothing at all, whatever the client presented. */
    REFUSES_EVERYONE,
    /**
     * Ends the handshake when a client presents any certificate, as a real device does with one
     * that its own authority did not issue, and answers a client that presents none.
     */
    REJECTS_PRESENTED_CERTIFICATES
  }

  private final SSLServerSocket server;
  private final Thread acceptor;
  private final List<String> peers = Collections.synchronizedList(new ArrayList<>());
  private final List<String> paths = Collections.synchronizedList(new ArrayList<>());
  private final X509Certificate listedCa;

  /** What the device does with requests. */
  public volatile Behaviour behaviour = Behaviour.ANSWERS_EVERYONE;

  /**
   * Whether a successful login brings a cookie. A real device issues none to a client that
   * presented its certificate.
   */
  public volatile boolean issuesLoginCookie = true;

  /**
   * Starts the listener on a free local port.
   *
   * @throws Exception if the keystore or the socket cannot be set up
   */
  public TlsTestDevice() throws Exception {
    var keystore = KeyStore.getInstance("PKCS12");
    var password = TestPki.SERVER_KEYSTORE_PASSWORD.toCharArray();
    keystore.load(
        new ByteArrayInputStream(Base64.getDecoder().decode(TestPki.SERVER_KEYSTORE_BASE64)),
        password);
    var keyManagers = KeyManagerFactory.getInstance("SunX509");
    keyManagers.init(keystore, password);
    listedCa = (X509Certificate) keystore.getCertificate("server");
    var context = SSLContext.getInstance("TLS");
    context.init(keyManagers.getKeyManagers(), new TrustManager[] {new ListsOneCa()}, null);
    server =
        (SSLServerSocket)
            context
                .getServerSocketFactory()
                .createServerSocket(0, 5, InetAddress.getLoopbackAddress());
    server.setWantClientAuth(true);
    server.setEnabledProtocols(new String[] {"TLSv1.2"});
    acceptor = Thread.ofPlatform().daemon().start(this::serve);
  }

  /** Lists the server certificate as the only acceptable CA and trusts any client certificate. */
  private final class ListsOneCa implements X509TrustManager {
    @Override
    public X509Certificate[] getAcceptedIssuers() {
      return new X509Certificate[] {listedCa};
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType)
        throws java.security.cert.CertificateException {
      if (behaviour == Behaviour.REJECTS_PRESENTED_CERTIFICATES) {
        throw new java.security.cert.CertificateException("not issued by the device's authority");
      }
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
  }

  /**
   * Returns the port the listener is on.
   *
   * @return the TCP port
   */
  public int port() {
    return server.getLocalPort();
  }

  /**
   * Returns, for every request received, the subject of the client certificate presented, or {@code
   * none}.
   *
   * @return a snapshot, in the order the requests arrived
   */
  public List<String> peers() {
    synchronized (peers) {
      return List.copyOf(peers);
    }
  }

  /**
   * Returns the path of every request received.
   *
   * @return a snapshot, in the order the requests arrived
   */
  public List<String> paths() {
    synchronized (paths) {
      return List.copyOf(paths);
    }
  }

  private void serve() {
    while (!server.isClosed()) {
      try (var socket = (SSLSocket) server.accept()) {
        handle(socket);
      } catch (IOException _) {
        // a failed handshake or a closed server: the client sees the failure
      }
    }
  }

  private void handle(SSLSocket socket) throws IOException {
    var reader =
        new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
    var requestLine = reader.readLine();
    String line;
    var contentLength = 0;
    while ((line = reader.readLine()) != null && !line.isEmpty()) {
      if (line.toLowerCase().startsWith("content-length:")) {
        contentLength = Integer.parseInt(line.substring(line.indexOf(':') + 1).strip());
      }
    }
    reader.skip(contentLength);
    var peer = peerSubject(socket);
    peers.add(peer);
    var path = requestLine.split(" ")[1];
    paths.add(path);
    var answer = answerTo(path, peer);
    var bytes = answer.getBytes(StandardCharsets.UTF_8);
    var cookie =
        path.startsWith("/cgi-bin/login.cgi") && bytes.length > 0 && issuesLoginCookie
            ? "Set-Cookie: dtmauth=TLSTOKEN; path=/\r\n"
            : "";
    var head =
        "HTTP/1.1 200 OK\r\nContent-Type: text/xml\r\n"
            + cookie
            + "Content-Length: "
            + bytes.length
            + "\r\nConnection: close\r\n\r\n";
    socket.getOutputStream().write(head.getBytes(StandardCharsets.US_ASCII));
    socket.getOutputStream().write(bytes);
    socket.getOutputStream().flush();
  }

  private static String peerSubject(SSLSocket socket) {
    try {
      return ((X509Certificate) socket.getSession().getPeerCertificates()[0])
          .getSubjectX500Principal()
          .getName();
    } catch (SSLPeerUnverifiedException _) {
      return "none";
    }
  }

  private String answerTo(String path, String peer) {
    var refuse =
        behaviour == Behaviour.REFUSES_EVERYONE
            || (behaviour == Behaviour.REFUSES_WITHOUT_CERTIFICATE && peer.equals("none"));
    if (refuse) {
      return "";
    }
    if (path.startsWith("/cgi-bin/login.cgi")) {
      return "<RimTabletResponse><Auth><Status>Success</Status><Smb><Credentials User='dtm'"
                 + " Password='TLS-SHARE'/><User>dtm</User><Pwd>TLS-SHARE</Pwd>"
                 + "<Workgroup>WORKGROUP</Workgroup><NetBios>%h</NetBios></Smb></Auth></RimTabletResponse>";
    }
    if (path.startsWith("/cgi-bin/wipe.cgi")) {
      return "<RimTabletResponse><DevicePIN><Status>Success</Status><PIN>PINX2A3B4C5D</PIN></DevicePIN></RimTabletResponse>";
    }
    // the name of the device is what the client certificate said, so a test can read it back
    return "<RimTabletResponse><DynamicProperties><Status>Success</Status><DeviceInformation>"
        + "<DeviceName>"
        + peer.replace("&", "&amp;").replace("<", "&lt;")
        + "</DeviceName></DeviceInformation></DynamicProperties></RimTabletResponse>";
  }

  @Override
  public void close() throws IOException {
    server.close();
    try {
      acceptor.join(2000);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}
