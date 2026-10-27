package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import com.jbraddockm.tunnel.internal.Hex;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link UdpDatagramTransport}: the BouncyCastle {@code DatagramTransport} over UDP. Its
 * receive path floors the timeout at 30&nbsp;s (the device is slow), so that branch is exercised
 * live rather than here; these cover the limits, sending, and close.
 */
@DisplayName("UdpDatagramTransport: UDP under BouncyCastle DTLS")
class UdpDatagramTransportTest {

  @Nested
  @DisplayName("the receive and send limits")
  class Limits {

    @Test
    @DisplayName("leave room for the IPv4 (20) and UDP (8) headers under the MTU")
    void leaveRoomForTheIpAndUdpHeaders() throws Exception {
      try (DatagramSocket s = new DatagramSocket()) {
        UdpDatagramTransport t = new UdpDatagramTransport(s, 1500);
        assertThat(t.getReceiveLimit()).isEqualTo(1500 - 20 - 8);
        assertThat(t.getSendLimit()).isEqualTo(1500 - 20 - 8);
      }
    }
  }

  @Nested
  @DisplayName("sending")
  class Sending {

    @Test
    @DisplayName("delivers the payload over UDP, honouring the offset and length")
    void deliversThePayloadOverUdp() throws Exception {
      InetAddress lo = InetAddress.getLoopbackAddress();
      try (DatagramSocket receiver = new DatagramSocket(new InetSocketAddress(lo, 0));
          DatagramSocket sender = new DatagramSocket()) {
        sender.connect(lo, receiver.getLocalPort());
        UdpDatagramTransport t = new UdpDatagramTransport(sender, 1500);

        byte[] framed =
            Hex.fromHex("ffff" + "0001deadbeef"); // 2 leading bytes skipped via the offset
        t.send(framed, 2, framed.length - 2);

        receiver.setSoTimeout(2000);
        byte[] buf = new byte[64];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        receiver.receive(p);
        assertThat(Arrays.copyOf(p.getData(), p.getLength()))
            .containsExactly(Hex.fromHex("0001deadbeef"));
      }
    }
  }

  @Nested
  @DisplayName("closing")
  class Closing {

    @Test
    @DisplayName("closes the underlying socket")
    void closesTheUnderlyingSocket() throws Exception {
      DatagramSocket s = new DatagramSocket();
      UdpDatagramTransport t = new UdpDatagramTransport(s, 1500);
      t.close();
      assertThat(s.isClosed()).isTrue();
    }
  }
}
