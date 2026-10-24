package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ControlChannel}: the {@code [00 cmd len len payload]} framing — writing frames,
 * reading them, skipping async NOTIFY, and throwing on ERROR.
 */
@DisplayName("ControlChannel: the framed control protocol")
class ControlChannelTest {

  private static ControlChannel over(byte[] inbound, ByteArrayOutputStream out) {
    return new ControlChannel(out, new ByteArrayInputStream(inbound));
  }

  @Nested
  @DisplayName("sending a frame")
  class Sending {

    @Test
    @DisplayName("writes the 0x00/cmd header then the payload")
    void writesHeaderThenPayload() throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      over(new byte[0], out).send(0x02, Hex.fromHex("aabbcc"));
      assertThat(out.toByteArray()).containsExactly(Hex.fromHex("000200" + "03" + "aabbcc"));
    }

    @Test
    @DisplayName("encodes the payload length as a 16-bit big-endian field")
    void encodesA16BitBigEndianLength() throws IOException {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      byte[] payload = new byte[0x0120]; // 288 -> length bytes 0x01 0x20
      over(new byte[0], out).send(0x06, payload);

      byte[] written = out.toByteArray();
      assertThat(new byte[] {written[0], written[1], written[2], written[3]})
          .containsExactly(0x00, 0x06, 0x01, 0x20);
      assertThat(written).hasSize(4 + payload.length);
    }
  }

  @Nested
  @DisplayName("reading a frame")
  class Reading {

    @Test
    @DisplayName("returns the frame whose command was asked for")
    void returnsTheMatchingFrame() throws IOException {
      ControlChannel ctl = over(Hex.fromHex("0003" + "0001" + "99"), new ByteArrayOutputStream());
      ControlChannel.Frame f = ctl.read(0x03);
      assertThat(f.cmd).isEqualTo(0x03);
      assertThat(f.body).containsExactly(0x99);
    }

    @Test
    @DisplayName("transparently skips an async NOTIFY (0x05) that precedes the awaited frame")
    void skipsAsyncNotifyFrames() throws IOException {
      byte[] stream = Hex.fromHex("0005" + "0002" + "aabb" + "0003" + "0001" + "99");
      ControlChannel.Frame f = over(stream, new ByteArrayOutputStream()).read(0x03);
      assertThat(f.cmd).isEqualTo(0x03);
      assertThat(f.body).containsExactly(0x99);
    }

    @Test
    @DisplayName("throws when the device sends an ERROR (0x04) frame")
    void throwsOnErrorFrame() {
      ControlChannel ctl = over(Hex.fromHex("0004" + "0002" + "dead"), new ByteArrayOutputStream());
      assertThatThrownBy(() -> ctl.read(0x03))
          .isInstanceOf(IOException.class)
          .hasMessageContaining("ERROR");
    }

    @Test
    @DisplayName("throws EOF when the stream ends part-way through a frame")
    void throwsEofWhenStreamEndsMidFrame() {
      ControlChannel ctl = over(Hex.fromHex("0003" + "00"), new ByteArrayOutputStream());
      assertThatThrownBy(() -> ctl.read(0x03)).isInstanceOf(EOFException.class);
    }

    @Test
    @DisplayName("reassembles a frame delivered one byte per read()")
    void reassemblesAcrossPartialReads() throws IOException {
      byte[] frame = Hex.fromHex("0003" + "0003" + "010203");
      InputStream oneByteAtATime =
          new InputStream() {
            int i = 0;

            @Override
            public int read() {
              return i < frame.length ? (frame[i++] & 0xff) : -1;
            }

            @Override
            public int read(byte[] b, int off, int len) {
              if (i >= frame.length) return -1;
              b[off] = frame[i++];
              return 1; // deliberately one byte at a time
            }
          };
      ControlChannel.Frame f =
          new ControlChannel(new ByteArrayOutputStream(), oneByteAtATime).read(0x03);
      assertThat(f.body).containsExactly(0x01, 0x02, 0x03);
    }
  }

  @Nested
  @DisplayName("parsing the IPv4 restrictions reply")
  class Ipv4Restrictions {

    @Test
    @DisplayName("walks a counted list of address/mask pairs without failing")
    void handlesACountedList() {
      byte[] body = Hex.fromHex("0001" + "7f000001" + "ff000000"); // count=1, 127.0.0.1 /8
      ControlChannel.parseIpv4Restrictions(body);
    }
  }
}
