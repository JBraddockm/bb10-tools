package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link RimPacket}: the 24-byte keepalive reply that holds the DTLS data gate open. */
@DisplayName("RimPacket: the keepalive reply")
class RimPacketTest {

  /** A device PING: tag 00 02, type 00 01, 8 timestamp bytes, then 00 00 ea 60 (lease). */
  private static byte[] samplePing() {
    return Hex.fromHex("0002" + "0001" + "1122334455667788" + "0000ea60");
  }

  @Nested
  @DisplayName("keepAliveReply, given a device PING")
  class KeepAliveReply {

    @Test
    @DisplayName("is exactly 24 bytes (a shorter echo is ignored by the device)")
    void isExactly24Bytes() {
      assertThat(RimPacket.keepAliveReply(samplePing())).hasSize(24);
    }

    @Test
    @DisplayName("carries the control tag 00 02 and the PONG type 00 02")
    void carriesTheControlTagAndPongType() {
      byte[] r = RimPacket.keepAliveReply(samplePing());
      assertThat(Arrays.copyOfRange(r, 0, 4)).containsExactly(0x00, 0x02, 0x00, 0x02);
    }

    @Test
    @DisplayName("echoes the device's eight timestamp bytes")
    void echoesTheDevicesEightTimestampBytes() {
      byte[] r = RimPacket.keepAliveReply(samplePing());
      assertThat(Arrays.copyOfRange(r, 4, 12)).containsExactly(Hex.fromHex("1122334455667788"));
    }

    @Test
    @DisplayName("appends our own timestamp and leaves the final four bytes zero")
    void appendsOurTimestampAndTrailingZeros() {
      byte[] r = RimPacket.keepAliveReply(samplePing());
      assertThat(Arrays.copyOfRange(r, 12, 20)).isNotEqualTo(new byte[8]);
      assertThat(Arrays.copyOfRange(r, 20, 24)).containsOnly((byte) 0);
    }

    @Test
    @DisplayName("preserves the channel-tag high byte taken from the PING")
    void preservesTheTagHighByteFromThePing() {
      byte[] ping = samplePing();
      ping[0] = 0x00;
      ping[1] = 0x02;
      byte[] r = RimPacket.keepAliveReply(ping);
      assertThat(r[0]).isEqualTo(ping[0]);
      assertThat(r[1]).isEqualTo(ping[1]);
    }
  }
}
