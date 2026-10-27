package com.jbraddockm.tunnel;

import com.jbraddockm.tunnel.internal.Hex;
import java.io.*;

/**
 * The framed command protocol spoken over the TLS control channel (TCP :1111).
 *
 * <p><b>Frame format:</b> {@code [0x00][cmd][len_hi][len_lo][payload...]}. The first byte is always
 * 0; the second is the command; the next two are the big-endian payload length; then the payload.
 * Responses use the request command with bit 0 set (request even, response odd): INIT 0x00 →
 * INIT_RESP 0x01, CONNECT 0x02 → 0x03, IPV4_ENABLE 0x06 → 0x07, etc.
 *
 * <p><b>Dispatch, never positional.</b> The device interleaves asynchronous NOTIFY frames (cmd
 * 0x05) at any time — a cold-start session even prepends a ~141-byte 0x05 announcement before the
 * reply you asked for. So {@link #read} loops on the 4-byte header and skips 0x05 rather than
 * assuming a fixed order.
 */
final class ControlChannel {
  final OutputStream out;
  final InputStream in;

  ControlChannel(OutputStream out, InputStream in) {
    this.out = out;
    this.in = in;
  }

  /**
   * Parses and prints the IPV4_INQUIRE response (0x0b): {@code [u16 count][ (u32 addr, u32 mask) ×
   * count ]}. These are the device's AddressAllocator restrictions (e.g. 127.0.0.1/8,
   * 192.168.1.118/24), i.e. which inner addresses it will allocate — <i>not</i> the live transport
   * addressing.
   */
  static void parseIpv4Restrictions(byte[] body) {
    int count = ((body[0] & 0xff) << 8) | (body[1] & 0xff);
    IO.println("IPV4 restrictions (" + count + "):");
    int p = 2;
    for (int i = 0; i < count; i++) {
      int m =
          ((body[p + 4] & 0xff) << 24)
              | ((body[p + 5] & 0xff) << 16)
              | ((body[p + 6] & 0xff) << 8)
              | (body[p + 7] & 0xff);
      IO.println(
          "  " + Hex.ipv4ToString(body, p) + "/" + Integer.bitCount(m)); // mask → prefix length
      p += 8;
    }
  }

  /** Writes one command frame: {@code 00 cmd len_hi len_lo payload}. */
  void send(int cmd, byte[] payload) throws IOException {
    int len = payload.length;
    out.write(new byte[] {0x00, (byte) cmd, (byte) (len >> 8), (byte) len});
    out.write(payload);
    out.flush();
  }

  /**
   * Reads frames until one matching {@code wantCmd} arrives, transparently skipping async NOTIFY
   * (0x05) and throwing on ERROR (0x04). Pass {@code wantCmd < 0} to accept the next non-notify
   * frame.
   *
   * @throws IOException if the device sends ERROR (0x04) or the stream ends
   */
  Frame read(int wantCmd) throws IOException {
    while (true) {
      byte[] h = readExactly(4); // header: 00 cmd len_hi len_lo
      int cmd = h[1] & 0xff;
      int len = ((h[2] & 0xff) << 8) | (h[3] & 0xff);
      byte[] body = readExactly(len);
      if (cmd == 0x05) { // async NOTIFY (keepalive / service-change) — skip and keep reading
        IO.println("  (NOTIFY 0x05, " + len + "B, skipped)");
        continue;
      }
      if (cmd == 0x04) throw new IOException("device sent ERROR (0x04): " + Hex.toHex(body));
      if (wantCmd >= 0 && cmd != wantCmd)
        IO.println("  (unexpected cmd 0x" + Integer.toHexString(cmd) + ", using anyway)");
      return new Frame(cmd, body);
    }
  }

  /** Reads exactly {@code n} bytes or throws EOF. */
  private byte[] readExactly(int n) throws IOException {
    byte[] b = new byte[n];
    int off = 0;
    while (off < n) {
      int r = in.read(b, off, n - off);
      if (r < 0) throw new EOFException();
      off += r;
    }
    return b;
  }

  /** A decoded control frame: command byte + payload. */
  record Frame(int cmd, byte[] body) {}
}
