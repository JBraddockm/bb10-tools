package com.jbraddockm.tunnel;

/**
 * Builders for the packets that travel inside the DTLS data channel.
 *
 * <p>Every decrypted DTLS datagram begins with a 2-byte channel tag:
 *
 * <ul>
 *   <li>{@code 0x0002} — RimPacket control (ping / keepalive)
 *   <li>{@code 0x0003} — IPv4 data: a raw IPv4 packet follows
 *   <li>{@code 0x0001} — IPv6 data: a raw IPv6 packet follows
 * </ul>
 */
final class RimPacket {
  private RimPacket() {}

  /**
   * Builds the 24-byte RimPacket PING reply that opens (and holds open) the data gate.
   *
   * <p>After the DTLS handshake the device sends a PING: {@code 00 02 00 01 | <8B device
   * timestamps> | 00 00 ea 60} (the {@code ea60} = 60000 ms keepalive lease). We must reply with
   * <b>24 bytes</b>: {@code 00 02 00 02 | echo of ping[4:12] (the device's 8 timestamp bytes) | <4B
   * our ts hi> <4B our ts lo>}. A shorter (16-byte) echo is <i>not</i> enough — the device keeps
   * re-pinging until it gets the full 24-byte reply, then it starts sending data. Keep answering
   * these for the tunnel's lifetime.
   *
   * @param ping the received PING frame (starting with its 2-byte 0x0002 tag)
   */
  static byte[] keepAliveReply(byte[] ping) {
    byte[] r = new byte[24];
    r[0] = ping[0]; // 0x00  — copy the channel tag high byte
    r[1] = ping[1]; // 0x02  — RimPacket control
    r[2] = 0x00;
    r[3] = 0x02; // type 0x0002 = PONG/keepalive reply
    System.arraycopy(ping, 4, r, 4, 8); // echo the device's 8 timestamp bytes (ping[4:12])
    // Append our own timestamp as two 32-bit words. (The device only checks that we echo its bytes
    // and
    // send plausible timestamps back; the exact clock value is not validated.)
    long now = System.currentTimeMillis();
    int hi = (int) (now >>> 8), lo = (int) now;
    r[12] = (byte) (hi >> 24);
    r[13] = (byte) (hi >> 16);
    r[14] = (byte) (hi >> 8);
    r[15] = (byte) hi;
    r[16] = (byte) (lo >> 24);
    r[17] = (byte) (lo >> 16);
    r[18] = (byte) (lo >> 8);
    r[19] = (byte) lo;
    // r[20..24] left zero.
    return r;
  }
}
