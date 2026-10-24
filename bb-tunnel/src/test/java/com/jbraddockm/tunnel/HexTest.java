package com.jbraddockm.tunnel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Tests for {@link Hex}: byte/hex and IPv4/IPv6 conversions, and the internet checksum. */
@DisplayName("Hex: bytes, addresses and the internet checksum")
class HexTest {

  @Nested
  @DisplayName("hex strings")
  class HexStrings {

    @Test
    @DisplayName("round-trip through bytes and come back lowercase with no separators")
    void roundTripsAndIsLowercase() {
      byte[] bytes = {0x24, 0x05, 0x20, 0x03, (byte) 0xAB};
      assertThat(Hex.toHex(bytes)).isEqualTo("24052003ab");
      assertThat(Hex.fromHex("24052003ab")).containsExactly(bytes);
      assertThat(Hex.fromHex(Hex.toHex(bytes))).containsExactly(bytes);
    }
  }

  @Nested
  @DisplayName("IPv4 addresses")
  class Ipv4 {

    @Test
    @DisplayName("convert to and from four bytes, honouring the offset into the array")
    void roundTripsAndHonoursOffset() {
      assertThat(Hex.ipv4ToBytes("192.168.1.118")).containsExactly(192, 168, 1, 118);
      assertThat(Hex.ipv4ToString(new byte[] {(byte) 192, (byte) 168, 1, 118}, 0))
          .isEqualTo("192.168.1.118");
      assertThat(Hex.ipv4ToString(new byte[] {0, 0, (byte) 169, (byte) 254, 0, 1}, 2))
          .isEqualTo("169.254.0.1");
    }
  }

  @Nested
  @DisplayName("IPv6 addresses")
  class Ipv6 {

    @Test
    @DisplayName("render as eight fully-expanded groups, with no \"::\" compression")
    void stringIsFullyExpandedEightGroups() {
      byte[] ula = Hex.ipv6ToBytes("fd32:5bd4:58d2:3549:b594:f946:addb:bd94");
      assertThat(ula).hasSize(16);
      assertThat(Hex.ipv6ToString(ula, 0)).isEqualTo("fd32:5bd4:58d2:3549:b594:f946:addb:bd94");
    }

    @Test
    @DisplayName("expand a \"::\" by filling the head from the left and the tail from the right")
    void toBytesExpandsDoubleColon() {
      assertThat(Hex.ipv6ToString(Hex.ipv6ToBytes("fe80::2"), 0))
          .isEqualTo("fe80:0000:0000:0000:0000:0000:0000:0002");
      assertThat(Hex.ipv6ToString(Hex.ipv6ToBytes("::1"), 0))
          .isEqualTo("0000:0000:0000:0000:0000:0000:0000:0001");
      assertThat(Hex.ipv6ToString(Hex.ipv6ToBytes("fd00::"), 0))
          .isEqualTo("fd00:0000:0000:0000:0000:0000:0000:0000");
    }

    @Test
    @DisplayName("round-trip the Blend ULA unchanged")
    void roundTripsForTheBlendUla() {
      String ula = "fd00:0000:0000:2fc2:4132:a6e5:2b64:8fdb";
      assertThat(Hex.ipv6ToString(Hex.ipv6ToBytes(ula), 0)).isEqualTo(ula);
    }
  }

  @Nested
  @DisplayName("the internet checksum")
  class Checksum {

    @Test
    @DisplayName("folds the carries and inverts (all-zero words become 0xffff, all-ones become 0)")
    void foldsAndInverts() {
      assertThat(Hex.checksum(new byte[] {0x00, 0x00}, 0, 2)).isEqualTo(0xffff);
      assertThat(Hex.checksum(new byte[] {(byte) 0xff, (byte) 0xff}, 0, 2)).isEqualTo(0x0000);
      assertThat(Hex.checksum(new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff}, 0, 4))
          .isEqualTo(0x0000);
    }

    @Test
    @DisplayName("sums to zero over a buffer that already carries its own checksum")
    void isVerifiableWhenAppended() {
      byte[] data = {0x45, 0x00, 0x00, 0x3c, 0x1c, 0x46, 0x40, 0x00};
      int cs = Hex.checksum(data, 0, data.length);

      byte[] withCs = new byte[data.length + 2];
      System.arraycopy(data, 0, withCs, 0, data.length);
      withCs[data.length] = (byte) (cs >> 8);
      withCs[data.length + 1] = (byte) cs;

      assertThat(Hex.checksum(withCs, 0, withCs.length)).isZero();
    }
  }
}
