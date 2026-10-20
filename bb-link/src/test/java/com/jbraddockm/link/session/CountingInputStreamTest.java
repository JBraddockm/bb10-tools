package com.jbraddockm.link.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests for {@link CountingInputStream}: progress reporting for files sent to the device. */
@DisplayName("CountingInputStream: tells how much of a file has been read")
class CountingInputStreamTest {

  private static final int KIBIBYTE = 1024;

  private final List<Long> reported = new ArrayList<>();

  private CountingInputStream streamOf(int size, long declaredLength) {
    var data = new byte[size];
    for (var index = 0; index < size; index++) {
      data[index] = (byte) index;
    }
    return new CountingInputStream(new ByteArrayInputStream(data), declaredLength, reported::add);
  }

  private static void readAll(CountingInputStream stream, int bufferSize) throws IOException {
    var buffer = new byte[bufferSize];
    while (stream.read(buffer, 0, buffer.length) >= 0) {
      // reading is all there is to do
    }
  }

  @Test
  @DisplayName("reports at each 64 KiB of a small file, and not for what is left over at the end")
  void reportsEvery64Kibibytes() throws IOException {
    var stream = streamOf(200 * KIBIBYTE, 200L * KIBIBYTE);

    readAll(stream, 16 * KIBIBYTE);

    assertThat(reported).containsExactly(64L * KIBIBYTE, 128L * KIBIBYTE, 192L * KIBIBYTE);
  }

  @Test
  @DisplayName(
      "takes a step of a hundredth of the length for a large file: 1 MiB for 100 MiB, so that it"
          + " does not report too often")
  void theStepGrowsWithTheLength() throws IOException {
    var stream = streamOf(300 * KIBIBYTE, 100L * KIBIBYTE * KIBIBYTE);

    readAll(stream, 16 * KIBIBYTE);

    assertThat(reported).isEmpty();
  }

  @Test
  @DisplayName(
      "counts the bytes that are skipped as read: what is reported is the position in the file")
  void countsSkippedBytes() throws IOException {
    var stream = streamOf(200 * KIBIBYTE, 200L * KIBIBYTE);

    var skipped = stream.skip(70L * KIBIBYTE);

    assertThat(skipped).isEqualTo(70L * KIBIBYTE);
    assertThat(reported).containsExactly(70L * KIBIBYTE);
  }

  @Test
  @DisplayName(
      "cannot be marked or reset, though its source can, so that the count never goes back")
  void cannotBeReset() {
    var stream = streamOf(10, 10);

    stream.mark(5);

    assertThat(stream.markSupported()).isFalse();
    assertThatThrownBy(stream::reset)
        .isInstanceOf(IOException.class)
        .hasMessage("mark/reset not supported");
  }

  @Test
  @DisplayName("reports the same whether it is read a byte at a time or in blocks")
  void countsBytesAndBlocksAlike() throws IOException {
    var stream = streamOf(130 * KIBIBYTE, 130L * KIBIBYTE);

    while (stream.read() >= 0) {
      // one byte at a time
    }

    assertThat(reported).containsExactly(64L * KIBIBYTE, 128L * KIBIBYTE);
  }

  @Test
  @DisplayName("does not count the end of the stream, and does not report for a file that is empty")
  void doesNotCountTheEnd() throws IOException {
    var stream = streamOf(0, 0);

    assertThat(stream.read()).isEqualTo(-1);
    assertThat(stream.read(new byte[8], 0, 8)).isEqualTo(-1);
    assertThat(reported).isEmpty();
  }

  @Test
  @DisplayName("passes the bytes through unchanged")
  void passesTheBytesThrough() throws IOException {
    var stream = streamOf(300, 300);

    var all = stream.readAllBytes();

    assertThat(all).hasSize(300);
    assertThat(all[0]).isEqualTo((byte) 0);
    assertThat(all[299]).isEqualTo((byte) 299);
  }
}
