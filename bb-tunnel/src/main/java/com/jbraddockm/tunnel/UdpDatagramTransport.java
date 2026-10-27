package com.jbraddockm.tunnel;

import java.io.IOException;
import java.net.*;
import org.bouncycastle.tls.DatagramTransport;

/**
 * Adapts a {@code connect()}ed {@link DatagramSocket} to BouncyCastle's {@link DatagramTransport}
 * so the DTLS client can run over UDP.
 *
 * <p>Two device-specific details matter here:
 *
 * <ul>
 *   <li><b>PortUnreachableException → -1.</b> The busy device sometimes emits stale ICMP
 *       port-unreachable for the ephemeral data port. We swallow it and return -1 (a benign "no
 *       datagram this round") so a transient ICMP does not abort the long DTLS handshake.
 *   <li><b>A long receive-timeout floor (30 s).</b> The device is slow: each DTLS flight takes ~20
 *       s of P-521 math on the Leap. Using at least 30 s stops BouncyCastle from retransmitting a
 *       flight before the device has answered — retransmits only make the device re-process and
 *       compound the delay.
 * </ul>
 */
final class UdpDatagramTransport implements DatagramTransport {
  final DatagramSocket socket;
  final int mtu;

  UdpDatagramTransport(DatagramSocket s, int mtu) {
    this.socket = s;
    this.mtu = mtu;
  }

  // Receive/send limits leave room for the IP (20) + UDP (8) headers under the MTU.
  @Override
  public int getReceiveLimit() {
    return mtu - 20 - 8;
  }

  @Override
  public int getSendLimit() {
    return mtu - 20 - 8;
  }

  @Override
  public int receive(byte[] buf, int off, int len, int waitMillis) throws IOException {
    socket.setSoTimeout(Math.max(waitMillis, 30000)); // floor the wait at 30 s — the device is slow
    DatagramPacket p = new DatagramPacket(buf, off, len);
    try {
      socket.receive(p);
      return p.getLength();
    } catch (SocketTimeoutException e) {
      return -1; // nothing arrived in time → let BC decide to retransmit
    } catch (PortUnreachableException e) {
      return -1; // stale ICMP from the busy device → ignore, don't abort the handshake
    }
  }

  @Override
  public void send(byte[] buf, int off, int len) throws IOException {
    socket.send(new DatagramPacket(buf, off, len));
  }

  @Override
  public void close() throws IOException {
    socket.close();
  }
}
