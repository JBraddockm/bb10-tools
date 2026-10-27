package com.jbraddockm.link.endpoint.pairing.pki;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.jbraddockm.link.testing.Resources;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Tests for {@link Pem}: writing and reading the text form of keys and certificates. */
@DisplayName("Pem: blocks of base64 between BEGIN and END lines")
class PemTest {

  private static String resource(String name) {
    return Resources.text(PemTest.class, name);
  }

  private static byte[] bytes(String text) {
    return text.getBytes(StandardCharsets.UTF_8);
  }

  @Nested
  @DisplayName("encode")
  class Encode {

    @Test
    @DisplayName(
        "writes the label, the content in base64 in lines of 64 characters, and the end line")
    void writesLinesOf64() {
      var content = new byte[100];
      IntStream.range(0, content.length).forEach(index -> content[index] = (byte) index);

      assertThat(Pem.encode("TEST DATA", content))
          .isEqualTo(resource("pem--long-content--expected.txt"));
    }

    @Test
    @DisplayName("writes a block of no content as the two lines alone")
    void writesAnEmptyBlock() {
      assertThat(Pem.encode("CERTIFICATE", new byte[0]))
          .isEqualTo(resource("pem--empty-content.txt"));
    }

    @ParameterizedTest(name = "{0} bytes are {1} lines of base64")
    @CsvSource({"1, 1", "48, 1", "49, 2", "96, 2", "97, 3"})
    @DisplayName("fills a line before it starts another, and never writes an empty line")
    void fillsLinesBeforeStartingAnother(int length, int lines) {
      var text = Pem.encode("X", new byte[length]);

      assertThat(text.split("\n")).hasSize(lines + 2);
      assertThat(text).doesNotContain("\n\n").endsWith("-----END X-----\n");
    }
  }

  @Nested
  @DisplayName("blocks")
  class Blocks {

    @Test
    @DisplayName("reads a block: its label and its bytes")
    void readsABlock() {
      var blocks = Pem.blocks(resource("pem--one-block.txt"));

      assertThat(blocks).hasSize(1);
      assertThat(blocks.getFirst().label()).isEqualTo("CERTIFICATE");
      assertThat(blocks.getFirst().content()).isEqualTo(bytes("dummy content"));
    }

    @Test
    @DisplayName("reads every block, in the order of the text")
    void readsEveryBlockInOrder() {
      var blocks = Pem.blocks(resource("pem--two-blocks.txt"));

      assertThat(blocks).extracting(Pem.Block::label).containsExactly("CERTIFICATE", "PRIVATE KEY");
      assertThat(blocks.get(1).content()).isEqualTo(bytes("second"));
    }

    @Test
    @DisplayName("ignores the text around a block, as an export of a certificate has")
    void ignoresTheTextAround() {
      assertThat(Pem.blocks(resource("pem--with-noise.txt")))
          .hasSize(1)
          .first()
          .satisfies(block -> assertThat(block.content()).isEqualTo(bytes("dummy content")));
    }

    @Test
    @DisplayName("reads lines that end in CR LF")
    void readsCarriageReturns() {
      assertThat(Pem.blocks(resource("pem--crlf.txt")))
          .hasSize(1)
          .first()
          .satisfies(block -> assertThat(block.content()).isEqualTo(bytes("dummy content")));
    }

    @Test
    @DisplayName("ignores the blanks and tabs inside the base64")
    void ignoresBlanksInTheBody() {
      assertThat(Pem.blocks(resource("pem--spaced-body.txt")))
          .hasSize(1)
          .first()
          .satisfies(block -> assertThat(block.content()).isEqualTo(bytes("dummy content")));
    }

    @Test
    @DisplayName("reads a block with no content as an empty one")
    void readsAnEmptyBlock() {
      assertThat(Pem.blocks(resource("pem--empty-content.txt")))
          .hasSize(1)
          .first()
          .satisfies(block -> assertThat(block.content()).isEmpty());
    }

    @ParameterizedTest(name = "{0} has no block")
    @ValueSource(
        strings = {"pem--no-block.txt", "pem--mismatched-labels.txt", "pem--lowercase-label.txt"})
    @DisplayName(
        "finds nothing in a text with no block, a BEGIN and END of different labels, or a label in"
            + " small letters")
    void findsNothing(String name) {
      assertThat(Pem.blocks(resource(name))).isEmpty();
    }

    @Test
    @DisplayName("refuses a block whose body is not base64")
    void refusesBadBase64() {
      var text = resource("pem--bad-base64.txt");

      assertThatIllegalArgumentException().isThrownBy(() -> Pem.blocks(text));
    }

    @Test
    @DisplayName("gives only the blocks with a label, when one is asked for")
    void filtersByLabel() {
      var text = resource("pem--two-blocks.txt");

      assertThat(Pem.blocks(text, "PRIVATE KEY"))
          .extracting(Pem.Block::label)
          .containsExactly("PRIVATE KEY");
      assertThat(Pem.blocks(text, "PUBLIC KEY")).isEmpty();
    }

    @Test
    @DisplayName("first gives the first block with a label, or nothing")
    void firstGivesTheFirstWithTheLabel() {
      var text = resource("pem--two-certificates.txt");

      assertThat(Pem.first(text, "CERTIFICATE"))
          .hasValueSatisfying(block -> assertThat(block.content()).isEqualTo(bytes("first")));
      assertThat(Pem.first(text, "PRIVATE KEY")).isEmpty();
    }
  }

  @Test
  @DisplayName("a block writes itself as the text it was read from")
  void aBlockWritesItself() {
    var text = resource("pem--one-block.txt");

    assertThat(Pem.blocks(text).getFirst().text()).isEqualTo(text);
  }

  @ParameterizedTest(name = "\"{0}\" is {1} bytes")
  @CsvSource({"'', 0", "abc, 3", "é, 2", "€, 3"})
  @DisplayName("size counts the bytes of the text in UTF-8, which is what the device counts")
  void sizeCountsBytes(String text, int expected) {
    assertThat(Pem.size(text)).isEqualTo(expected);
  }
}
